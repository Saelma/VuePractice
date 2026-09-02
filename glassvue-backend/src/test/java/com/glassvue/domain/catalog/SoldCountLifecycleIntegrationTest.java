package com.glassvue.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.entity.ProductVariant;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.repository.ProductVariantRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderItemCancelledEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import com.glassvue.domain.order.event.OrderReturnedEvent;
import com.glassvue.domain.order.event.SoldLine;
import com.glassvue.domain.point.service.PointService;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 🔴 <b>판매량({@code product.sold_count}) 이 주문 한살이를 돌고 나면 «남은 수량» 과 맞는가</b>
 * (2026-09-02, 「돈과 수량이 맞는가」 축).
 *
 * <p><b>왜 이 파일이 생겼나 — 운영 데이터가 어긋나 있었다.</b> 2026-09-02 아침 실측에서
 * 상품 <b>7개 중 6개</b>의 {@code sold_count} 가 실판매량과 달랐고, <b>전부 낮은 쪽</b>이었다
 * (지바 12 vs 20 · 반팔티 4 vs 33 · 몽쉘 9 vs 24). 경위는 {@code handoffs/2026-09-02-handoff.md}.
 *
 * <p>🔴 <b>어긋남을 만든 결함의 정체는 «되돌릴 때 어느 수량을 쓰느냐» 다</b> —
 * {@link SoldLine#ordered(com.glassvue.domain.order.entity.Order)} 로 되돌리면
 * <b>이미 되돌린 몫을 또 되돌린다</b>(2026-08-25, G-10 에서 발견). 그 위에
 * {@code addSoldCount} 가 <b>0 에서 바닥을 치므로</b> 넘친 몫은 영영 사라진다 —
 * <b>한 방향 래칫이라 되돌아오지 않는다.</b> 그래서 «한 번 새면 값으로는 못 고친다».
 *
 * <p>⚠ <b>그런데 그 결함을 잡는 테스트가 없었다.</b> 실측(2026-09-02):
 * {@code SoldLineTest} <b>1건</b>(옵션 합산만) · {@code SalesSyncHandlerTest} 3건(목으로 delta 확인) ·
 * {@code SalesEventListenerTest} 3건(<b>위임만</b> — 3층 컨벤션이 «기능 검증이 아니다» 라고 못 박은 층).
 * 🔴 <b>즉 «어느 팩토리를 골랐나» 를 묻는 테스트가 한 건도 없었다</b> — 팩토리는 넷인데 테스트는 하나다.
 *
 * <p><b>왜 {@code sold_count} 를 직접 안 읽는가</b> (WA §H-6): 갱신은
 * {@code @Async @TransactionalEventListener(AFTER_COMMIT)} 뒤에 있고 이 테스트는
 * {@code @Transactional} 롤백이라 <b>커밋이 없어 리스너가 아예 안 뜬다.</b> 컬럼을 읽으면
 * 언제나 0 이라 «초록인데 아무것도 안 본» 테스트가 된다.
 * → 대신 <b>리스너가 먹는 계약</b>인 «이벤트에 실린 {@link SoldLine}» 을 본다.
 * <b>결함이 살던 자리가 정확히 거기</b>이고(어느 팩토리를 골랐나), 핸들러가 그 값을 그대로
 * 더한다는 것은 {@code SalesSyncHandlerTest} 가 이미 지킨다.
 * ⚠ <b>«이벤트 합 == 남은 수량» 이 이 파일의 불변식이다.</b>
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class SoldCountLifecycleIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ApplicationEvents events;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired PointService pointService;

    private static final String JSON = "application/json";
    private static final String PW = "password123";

    private String buyerLoginId;
    private String adminLoginId;
    private UUID productId;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        buyerLoginId = "zzsc_b" + sfx;
        adminLoginId = "zzsc_a" + sfx;
        UUID buyerId = member(buyerLoginId, "ZZ판매량구매자", Role.USER);
        member(adminLoginId, "ZZ판매량관리자", Role.ADMIN);
        pointService.openAccount(buyerId);

        Category cat = categoryRepository.save(Category.builder().name("ZZC-판매량" + sfx).build());
        productId = productRepository.save(Product.builder()
                .name("ZZP-판매량" + sfx).description("d").price(10_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
        variantId = variantRepository.save(ProductVariant.of(productId, "기본", 0, 100, 0)).getId();
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role).build()).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /** 담기 → 주문 생성(ORDERED). 반환: orderId. */
    private String place(String buyer, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items").header("Authorization", buyer).contentType(JSON)
                .content("{\"variantId\":\"" + variantId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/orders").header("Authorization", buyer).contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울시 강남구 1\",\"address2\":null}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data");
    }

    /** 결제 → 발송 → 배송완료. 반품은 배송완료에서만 열린다. */
    private void deliver(String buyer, String admin, String orderId) throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", buyer))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + orderId + "/ship").header("Authorization", admin)
                .contentType(JSON).content("{\"carrier\":\"CJ\",\"trackingNo\":\"ZZ123\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + orderId + "/deliver").header("Authorization", admin))
                .andExpect(status().isOk());
    }

    private String firstItemId(String token, String orderId) throws Exception {
        String body = mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // ⚠ 칸 이름은 `id` 가 아니라 `orderItemId` 다 — 취소·반품 본문이 요구하는 이름과 같게 맞춰 둔 것이다.
        return JsonPath.read(body, "$.data.items[0].orderItemId");
    }

    private void cancelItem(String token, String orderId, String itemId, long qty) throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel-item").header("Authorization", token)
                .contentType(JSON)
                .content("{\"orderItemId\":\"" + itemId + "\",\"quantity\":" + qty + "}"))
                .andExpect(status().isOk());
    }

    private void returnAndApprove(String buyer, String admin, String orderId, String itemId, long qty)
            throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/return-request").header("Authorization", buyer)
                .contentType(JSON)
                .content("{\"reason\":\"ZZ-판매량 검증\",\"items\":[{\"orderItemId\":\"" + itemId
                        + "\",\"quantity\":" + qty + "}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + orderId + "/return-approve").header("Authorization", admin))
                .andExpect(status().isOk());
    }

    /**
     * 🔴 <b>이 파일의 심장.</b> 지금까지 발행된 판매량 이벤트들의 «+주문 / −되돌림» 을 합친다.
     *
     * <p>리스너가 하는 일과 <b>같은 순서·같은 부호</b>로 접는다({@code SalesEventListener} 참고):
     * {@code OrderPlacedEvent} 만 더하고 나머지 셋은 뺀다.
     * ⚠ <b>부호를 여기서 손으로 정하지 않는다</b> — 리스너가 어느 이벤트를 어느 쪽으로 쓰는지가
     * 곧 계약이라, 이벤트 종류가 늘면 <b>이 메서드가 먼저 빨개져야</b> 한다.
     */
    private long netSoldDelta() {
        long plus = linesOf(events.stream(OrderPlacedEvent.class).map(OrderPlacedEvent::lines));
        long minus = linesOf(Stream.of(
                        events.stream(OrderItemCancelledEvent.class).map(OrderItemCancelledEvent::lines),
                        events.stream(OrderCancelledEvent.class).map(OrderCancelledEvent::lines),
                        events.stream(OrderReturnedEvent.class).map(OrderReturnedEvent::lines))
                .flatMap(s -> s));
        return plus - minus;
    }

    /** 우리 상품의 줄만 골라 수량을 합친다 — 픽스처가 상품 하나뿐이라도 명시해 둔다. */
    private long linesOf(Stream<List<SoldLine>> lineLists) {
        return lineLists.flatMap(List::stream)
                .filter(l -> l.productId().equals(productId))
                .mapToLong(SoldLine::quantity)
                .sum();
    }

    // ────────────────────── 한살이별 «남은 수량» ──────────────────────

    @Test
    @DisplayName("주문만 하면 주문한 수량만큼 는다 — 기준선")
    void placed_increasesByOrderedQuantity() throws Exception {
        String buyer = login(buyerLoginId);
        place(buyer, 3);

        assertThat(netSoldDelta()).isEqualTo(3);
    }

    @Test
    @DisplayName("🔴 3개 중 1개 부분 취소 → 남은 2 — 되돌린 것은 «그 회차의 몫» 뿐이다")
    void partialCancel_reversesOnlyThatRound() throws Exception {
        String buyer = login(buyerLoginId);
        String orderId = place(buyer, 3);
        cancelItem(buyer, orderId, firstItemId(buyer, orderId), 1);

        // ⚠ 여기서 3 이 나오면 «부분 취소가 판매량을 안 되돌린다»(08-25 이전의 모양),
        //    0 이 나오면 «원본 수량으로 되돌렸다»(SoldLine.ordered 를 쓴 모양)다.
        assertThat(netSoldDelta()).isEqualTo(2);
    }

    @Test
    @DisplayName("🔴 부분 취소 뒤 부분 반품 — «이미 되돌린 몫을 또 되돌리는» 자리 (G-10 결함의 본체)")
    void partialCancelThenPartialReturn_doesNotDoubleReverse() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        String orderId = place(buyer, 3);
        String itemId = firstItemId(buyer, orderId);

        cancelItem(buyer, orderId, itemId, 1);          // 3 − 1 = 2
        deliver(buyer, admin, orderId);
        returnAndApprove(buyer, admin, orderId, itemId, 1);  // 2 − 1 = 1

        // 🔴 반품이 «원본 3» 으로 되돌리면 여기서 −1 이 되고, addSoldCount 의 바닥이 그것을 0 으로
        //    깎아 **그 손실이 영구가 된다**. 운영 데이터가 낮은 쪽으로만 어긋난 모양이 정확히 이것이다.
        assertThat(netSoldDelta()).isEqualTo(1);
    }

    @Test
    @DisplayName("🔴 부분 취소로 주문을 비우면 0 이 된다 — 품목 이벤트와 «주문 취소» 이벤트가 겹치는 자리")
    void partialCancelDrainsOrder_netsToZero() throws Exception {
        String buyer = login(buyerLoginId);
        String orderId = place(buyer, 3);
        String itemId = firstItemId(buyer, orderId);

        cancelItem(buyer, orderId, itemId, 1);
        cancelItem(buyer, orderId, itemId, 2);   // 마지막 — 주문이 비어 CANCELLED 가 된다

        // 🔴 마지막 회차엔 **이벤트가 둘** 난다(OrderItemCancelled + OrderCancelled.ofItemsDrained).
        //    뒤엣것이 «남은 것» 이 아니라 «원본» 으로 실리면 여기서 −3 이 된다.
        assertThat(netSoldDelta()).isEqualTo(0);
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("🔴 전량을 두 회차로 나눠 반품해도 0 이다 — 회차가 겹쳐도 총합은 주문 수량을 안 넘는다")
    void twoRoundReturn_netsToZero() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        String orderId = place(buyer, 3);
        String itemId = firstItemId(buyer, orderId);

        deliver(buyer, admin, orderId);
        returnAndApprove(buyer, admin, orderId, itemId, 1);
        returnAndApprove(buyer, admin, orderId, itemId, 2);

        assertThat(netSoldDelta()).isEqualTo(0);
    }

    @Test
    @DisplayName("전체 취소는 주문 수량을 그대로 되돌린다 — 부분이 한 번도 없었으면 원본과 같다")
    void fullCancel_netsToZero() throws Exception {
        String buyer = login(buyerLoginId);
        String orderId = place(buyer, 3);
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", buyer)
                .contentType(JSON).content("{\"reason\":\"ZZ-판매량 검증\"}"))
                .andExpect(status().isOk());

        assertThat(netSoldDelta()).isEqualTo(0);
    }
}
