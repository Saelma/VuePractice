package com.glassvue.domain.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductVariant;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.repository.ProductVariantRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.point.repository.PointAccountRepository;
import com.glassvue.domain.point.repository.PointHistoryRepository;
import com.glassvue.domain.point.service.PointService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적립금 · 회원 등급 (2026-07-24, 백로그 C-10).
 *
 * <p>여기서만 드러나는 것:
 * <ol>
 *   <li><b>잔액 == 이력의 합</b> — 이력이 원장이고 잔액은 캐시다. 잔액만 고치고 이력을 빠뜨리는
 *       코드를 잡는 유일한 장치라 <b>거의 모든 테스트가 마지막에 이걸 확인</b>한다.</li>
 *   <li><b>적립 기준액</b> — 배송비·쿠폰·사용 적립금을 뺀 "실제로 낸 상품 대금"인가.
 *       특히 적립금으로 낸 부분에 적립이 붙으면 <b>포인트가 포인트를 낳는다.</b></li>
 *   <li><b>배송완료 전에는 적립이 없다</b> — 결제만으로 주면 취소 시 회수해야 한다.</li>
 * </ol>
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PointFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired PointAccountRepository accountRepository;
    @Autowired PointHistoryRepository historyRepository;
    @Autowired PointService pointService;

    private static final String JSON = "application/json";
    // ⚠ 비밀번호 정책(E-3, 2026-07-30) 때문에 픽스처를 바꿨다 — password123 은 차단 목록에 있다.
    //    가입·비밀번호 변경 API 는 정책을 타므로, **API 로 만드는 계정**은 정책을 통과하는 값을 써야 한다.
    //    (리포지토리로 직접 저장하는 픽스처는 검증을 안 타므로 password123 을 그대로 쓴다.)
    private static final String PW = "Tulip-Harbor-72";

    private String buyerLoginId;
    private String adminLoginId;
    private UUID buyerId;
    private UUID productId;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        buyerLoginId = "pt_" + suffix;
        adminLoginId = "ptadmin_" + suffix;
        buyerId = member(buyerLoginId, "ZZ포인트구매자" + suffix, Role.USER);
        member(adminLoginId, "ZZ포인트관리자" + suffix, Role.ADMIN);
        // 리포지토리로 만든 회원은 signup 을 안 타므로 계정이 없다 — 직접 연다.
        pointService.openAccount(buyerId);

        Category cat = categoryRepository.save(Category.builder().name("ZZC-포인트" + suffix).build());
        productId = productRepository.save(Product.builder()
                .name("ZZP-포인트상품" + suffix).description("d").price(50_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
        variantId = variantRepository.save(ProductVariant.of(productId, "기본", 0, 1000, 0)).getId();
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW)).nickname(nickname).role(role).build()).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /** 상품 50,000 × quantity. 30,000 이상이라 배송비는 0이다. */
    private String order(String buyer, int quantity, Long usePoint) throws Exception {
        mockMvc.perform(post("/api/cart/items").header("Authorization", buyer).contentType(JSON)
                .content("{\"variantId\":\"" + variantId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/orders").header("Authorization", buyer).contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울시 강남구 테헤란로 1\",\"address2\":null"
                                + (usePoint == null ? "" : ",\"usePoint\":" + usePoint) + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data");
    }

    private void deliverFully(String buyer, String admin, String orderId) throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", buyer))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + orderId + "/ship").header("Authorization", admin)
                        .contentType(JSON).content("{\"carrier\":\"CJ\",\"trackingNo\":\"ZZ123456789\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + orderId + "/deliver").header("Authorization", admin))
                .andExpect(status().isOk());
    }

    /** 잔액이 이력의 합과 같은가 — 이 프로젝트에서 적립금이 어긋났는지 보는 유일한 방법. */
    private void assertLedgerConsistent() {
        long balance = accountRepository.findByMemberId(buyerId).orElseThrow().getBalance();
        assertThat(balance)
                .as("잔액은 이력의 합이어야 한다 (이력이 원장, 잔액은 캐시)")
                .isEqualTo(historyRepository.sumAmountByMemberId(buyerId));
    }

    @Test
    @DisplayName("가입하면 적립금 계정이 생긴다 — 0원 · BRONZE · 적립률 1%")
    void accountOpenedOnSignup() throws Exception {
        String loginId = "ptnew_" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/auth/signup").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW
                                + "\",\"nickname\":\"ZZ신규" + loginId.substring(6) + "\",\"email\":\"" + loginId + "@example.com\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/points/me").header("Authorization", login(loginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(0))
                .andExpect(jsonPath("$.data.grade").value("BRONZE"))
                .andExpect(jsonPath("$.data.earnPercent").value(1))
                .andExpect(jsonPath("$.data.nextGrade").value("SILVER"))
                // SILVER 임계 100,000 - 누적 0
                .andExpect(jsonPath("$.data.amountToNextGrade").value(100_000));
    }

    @Test
    @DisplayName("⚠ 배송완료 전에는 적립이 없다 — 결제만으로 주면 취소 시 회수해야 한다")
    void noEarnBeforeDelivery() throws Exception {
        String buyer = login(buyerLoginId);
        String orderId = order(buyer, 1, null);
        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", buyer))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/points/me").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.balance").value(0))
                .andExpect(jsonPath("$.data.totalPurchase").value(0));
        assertLedgerConsistent();
    }

    @Test
    @DisplayName("배송완료하면 적립된다 — 50,000의 1%(BRONZE) = 500")
    void earnOnDelivery() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        String orderId = order(buyer, 1, null);
        deliverFully(buyer, admin, orderId);

        mockMvc.perform(get("/api/points/me").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.balance").value(500))
                .andExpect(jsonPath("$.data.totalPurchase").value(50_000));

        // 주문에도 적립액이 스냅샷된다
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.earnedPoint").value(500));
        assertLedgerConsistent();
    }

    @Test
    @DisplayName("누적 100,000을 넘기면 SILVER 로 승급하고 **그 등급 적립률**이 바로 적용된다")
    void promotionAppliesImmediately() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);

        // 150,000 한 번에 → 누적 150,000 → SILVER(2%) → 3,000 적립
        deliverFully(buyer, admin, order(buyer, 3, null));

        mockMvc.perform(get("/api/points/me").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.grade").value("SILVER"))
                .andExpect(jsonPath("$.data.earnPercent").value(2))
                // 옛 등급(1%)이면 1,500. 승급 후 등급으로 계산하므로 3,000이어야 한다.
                .andExpect(jsonPath("$.data.balance").value(3_000));
        assertLedgerConsistent();
    }

    @Test
    @DisplayName("적립금을 주문에 쓰면 결제금액이 줄고 이력이 주문과 연결된다")
    void usePointOnOrder() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        deliverFully(buyer, admin, order(buyer, 1, null));   // 500 적립

        String orderId = order(buyer, 1, 500L);              // 50,000 − 500 = 49,500

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.usedPoint").value(500))
                .andExpect(jsonPath("$.data.payAmount").value(49_500));
        mockMvc.perform(get("/api/points/me").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.balance").value(0));

        // 이력이 "어느 주문 때문인지"를 담아야 한다 — 주문보다 먼저 차감하면 못 담는다
        assertThat(historyRepository.findByOrderId(UUID.fromString(orderId)))
                .as("사용 이력이 주문과 연결돼야 한다")
                .isNotEmpty();
        assertLedgerConsistent();
    }

    @Test
    @DisplayName("⚠ 적립금으로 낸 부분에는 적립이 안 붙는다 — 포인트가 포인트를 낳으면 안 된다")
    void noEarnOnPointPaidPortion() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        deliverFully(buyer, admin, order(buyer, 1, null));   // 누적 50,000 · 500 적립

        // 50,000짜리를 500 포인트 써서 산다 → 실제로 낸 상품 대금 49,500
        String orderId = order(buyer, 1, 500L);
        deliverFully(buyer, admin, orderId);

        // 누적 50,000 + 49,500 = 99,500 → 아직 BRONZE(1%) → 495 적립
        // 50,000 기준으로 계산했다면 500이 된다. 그 차이를 보는 테스트다.
        mockMvc.perform(get("/api/points/me").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.totalPurchase").value(99_500))
                .andExpect(jsonPath("$.data.balance").value(495));   // 0(다 씀) + 495
        assertLedgerConsistent();
    }

    @Test
    @DisplayName("잔액보다 많이 쓰려 하면 거절 — POINT-400N")
    void cannotUseMoreThanBalance() throws Exception {
        String buyer = login(buyerLoginId);
        mockMvc.perform(post("/api/cart/items").header("Authorization", buyer).contentType(JSON)
                .content("{\"variantId\":\"" + variantId + "\",\"quantity\":1}"));
        mockMvc.perform(post("/api/orders").header("Authorization", buyer).contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울시 강남구 1\",\"address2\":null,\"usePoint\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("POINT-400N"));
    }

    @Test
    @DisplayName("상품 금액보다 많이 쓰려 하면 거절 — 결제금액이 음수가 되면 안 된다")
    void cannotExceedOrderAmount() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        // 잔액을 크게 만든다: 150,000 구매 → SILVER 3,000 적립… 으로는 부족하므로 여러 번
        deliverFully(buyer, admin, order(buyer, 3, null));    // 누적 150,000 · 3,000
        deliverFully(buyer, admin, order(buyer, 40, null));   // 누적 2,150,000 · VIP 5% → 100,000

        long balance = accountRepository.findByMemberId(buyerId).orElseThrow().getBalance();
        assertThat(balance).isGreaterThan(50_000L);

        // 50,000짜리 주문에 잔액 전부를 쓰려 하면 상한(상품합계)을 넘는다
        mockMvc.perform(post("/api/cart/items").header("Authorization", buyer).contentType(JSON)
                .content("{\"variantId\":\"" + variantId + "\",\"quantity\":1}"));
        mockMvc.perform(post("/api/orders").header("Authorization", buyer).contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울시 강남구 1\",\"address2\":null,\"usePoint\":" + balance + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("POINT-400E"));
    }

    @Test
    @DisplayName("이력이 최신순으로 조회되고 부호가 맞다 (적립 +, 사용 −)")
    void history() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        deliverFully(buyer, admin, order(buyer, 1, null));   // EARN +500
        order(buyer, 1, 500L);                               // USE  −500

        mockMvc.perform(get("/api/points/me/history").header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].type").value("USE"))
                .andExpect(jsonPath("$.data.content[0].amount").value(-500))
                .andExpect(jsonPath("$.data.content[0].balanceAfter").value(0))
                .andExpect(jsonPath("$.data.content[1].type").value("EARN"))
                .andExpect(jsonPath("$.data.content[1].amount").value(500));
        assertLedgerConsistent();
    }

    @Test
    @DisplayName("적립금은 남에게 안 보인다 — 미인증 401")
    void requiresAuth() throws Exception {
        mockMvc.perform(get("/api/points/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/points/me/history")).andExpect(status().isUnauthorized());
    }
}
