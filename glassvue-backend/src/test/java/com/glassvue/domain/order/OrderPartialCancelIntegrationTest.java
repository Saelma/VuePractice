package com.glassvue.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.repository.AdminAuditLogRepository;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.entity.ProductVariant;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.repository.ProductVariantRepository;
import com.glassvue.domain.coupon.entity.Coupon;
import com.glassvue.domain.coupon.entity.DiscountType;
import com.glassvue.domain.coupon.entity.MemberCoupon;
import com.glassvue.domain.coupon.repository.CouponRepository;
import com.glassvue.domain.coupon.repository.MemberCouponRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import com.glassvue.domain.order.entity.OrderStatus;
import com.glassvue.domain.order.repository.OrderRepository;
import com.glassvue.domain.point.service.PointService;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 🔴 <b>부분 취소</b> {@code POST /api/orders/{id}/cancel-item} · {@code /admin-cancel-item}
 * (2026-08-24, BACKLOG G-4).
 *
 * <p><b>배분 산수는 여기서 안 본다</b> — {@code OrderPartialCancelTest}(순수 단위)가 수렴까지 고정한다.
 * 여기서 고정하는 것은 <b>단위 테스트로는 절대 안 잡히는 것들</b>이다:
 * <ul>
 *   <li><b>권한</b>(WA §2-4) — 실제 요청을 보내야만 드러난다.</li>
 *   <li>🔴 <b>쿠폰이 부분 취소로는 복구되지 않고, 전량 취소되는 순간 복구된다</b>(G-4 결정 1).
 *       되돌리는 것들이 한 줄에 모여 있지 않으면 하나씩 빠지는 자리다 — 2026-08-07 과 08-11 에
 *       같은 자리에서 적립금과 쿠폰이 차례로 빠졌다({@code applyCancellation} 주석).</li>
 *   <li>🔴 <b>{@code ORDER_ITEM_CANCEL} 이 CHECK 제약을 실제로 통과하는가</b> — Oracle enum CHECK 는
 *       {@code ddl-auto} 가 절대 안 고쳐서, V57 에서 손으로 갈지 않았으면 <b>여기서 ORA-02290</b> 이 난다.
 *       단위 테스트는 enum 값이 늘어난 것만 보고 <b>DB 가 그 값을 받는지는 안 본다.</b></li>
 *   <li><b>재고가 취소 수량만큼만</b> 돌아오는가.</li>
 * </ul>
 *
 * <p>DB_HOST 있을 때만 실행(= .env 소싱). {@code @Transactional} 롤백 → 공유 DB 무오염.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderPartialCancelIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired CouponRepository couponRepository;
    @Autowired MemberCouponRepository memberCouponRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired PointService pointService;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String PW = "password123";
    private static final String MARK = "ZZPARTCANCEL";

    private String adminLoginId;
    private String userLoginId;
    private String otherLoginId;
    private UUID userId;
    private UUID variantId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        adminLoginId = "zzpca_" + sfx;
        userLoginId = "zzpcu_" + sfx;
        otherLoginId = "zzpco_" + sfx;
        member(adminLoginId, MARK + "-관리자", Role.ADMIN);
        userId = member(userLoginId, MARK + "-구매자", Role.USER);
        member(otherLoginId, MARK + "-남", Role.USER);

        Category cat = categoryRepository.save(Category.builder().name("ZZC-" + MARK + sfx).build());
        productId = productRepository.save(Product.builder()
                .name("ZZP-" + MARK + sfx).description("d").price(10_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
        // 재고 5. 주문이 이미 나갔다고 보고, 취소하면 그만큼 **되돌아와야** 한다.
        variantId = variantRepository.save(ProductVariant.of(productId, "기본", 0, 5, 0)).getId();
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role).build()).getId();
    }

    /** A 20,000 · B 15,000 — G-4 검산 표본과 같은 모양. 쿠폰·적립금은 인자로 갈아 끼운다. */
    private Order order(String couponName, long couponDiscount, UUID memberCouponId, long usedPoint) {
        Order o = Order.create(userId, MARK + "-구매자",
                List.of(OrderItem.of(UUID.randomUUID(), variantId, null, MARK + "-A", null, 20_000, 20_000L, null, 1),
                        OrderItem.of(UUID.randomUUID(), variantId, null, MARK + "-B", null, 15_000, 15_000L, null, 1)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null, 0,
                "ZZ" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                couponName, couponDiscount, memberCouponId, usedPoint);
        return orderRepository.save(o);
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private ResultActions cancelItem(String path, UUID orderId, String auth, UUID itemId, long qty)
            throws Exception {
        var req = post("/api/orders/" + orderId + path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderItemId\":\"" + itemId + "\",\"quantity\":" + qty + "}");
        if (auth != null) {
            req = req.header("Authorization", auth);
        }
        return mockMvc.perform(req);
    }

    private UUID itemId(Order o, int index) {
        return o.getItems().get(index).getId();
    }

    private Order reload(UUID id) {
        return orderRepository.findById(id).orElseThrow();
    }

    // ────────────────────────── 권한 (WA §2-4) ──────────────────────────

    @Test
    @DisplayName("비로그인 → 401 (본인 경로·관리자 경로 둘 다)")
    void anonymous_unauthorized() throws Exception {
        Order o = order(null, 0, null, 0);
        cancelItem("/cancel-item", o.getId(), null, itemId(o, 0), 1).andExpect(status().isUnauthorized());
        cancelItem("/admin-cancel-item", o.getId(), null, itemId(o, 0), 1).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("🔴 일반 사용자는 관리자 경로를 못 쓴다 → 403 — 자기 주문이어도 그렇다")
    void user_forbidden_on_admin_path() throws Exception {
        // ⚠ 주문자 본인이라는 점이 중요하다 — 「남의 주문이라 막혔다」가 아니라 「경로가 관리자용」임을 본다.
        Order o = order(null, 0, null, 0);
        cancelItem("/admin-cancel-item", o.getId(), login(userLoginId), itemId(o, 0), 1)
                .andExpect(status().isForbidden());
        assertThat(reload(o.getId()).getCancelledItemsTotal()).isZero();
    }

    @Test
    @DisplayName("남의 주문은 본인 경로로도 못 건드린다 → 404 (있는지조차 알려주지 않는다)")
    void other_members_order_not_found() throws Exception {
        Order o = order(null, 0, null, 0);
        cancelItem("/cancel-item", o.getId(), login(otherLoginId), itemId(o, 0), 1)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("본인은 자기 주문의 품목을 뺄 수 있다 → 200")
    void owner_ok() throws Exception {
        Order o = order(null, 0, null, 0);
        cancelItem("/cancel-item", o.getId(), login(userLoginId), itemId(o, 1), 1).andExpect(status().isOk());

        Order after = reload(o.getId());
        assertThat(after.getStatus()).isEqualTo(OrderStatus.ORDERED); // 아직 A 가 남았다
        assertThat(after.remainingItemsTotal()).isEqualTo(20_000);
        assertThat(after.getPayAmount()).isEqualTo(20_000);
    }

    // ────────────────── 🔴 쿠폰 — G-4 결정 1 이 실제로 도는가 ──────────────────

    @Test
    @DisplayName("🔴 부분 취소는 쿠폰을 복구하지 않는다 — 최소금액을 소급하지 않기로 했다(결정 1)")
    void partialCancel_doesNotRestoreCoupon() throws Exception {
        Coupon coupon = couponRepository.save(Coupon.builder()
                .name(MARK + " 5천원").discountType(DiscountType.FIXED).discountValue(5_000L)
                .minOrderAmount(30_000L)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validUntil(Instant.now().plus(30, ChronoUnit.DAYS)).build());
        MemberCoupon mc = memberCouponRepository.save(MemberCoupon.issue(userId, coupon));
        mc.use();
        memberCouponRepository.flush();

        Order o = order(coupon.getName(), 5_000, mc.getId(), 0);

        // B(15,000) 만 뺀다 → 남은 20,000 은 최소금액 30,000 에 못 미친다.
        cancelItem("/cancel-item", o.getId(), login(userLoginId), itemId(o, 1), 1).andExpect(status().isOk());

        assertThat(memberCouponRepository.findById(mc.getId()).orElseThrow().isUsed())
                .as("부분 취소로 쿠폰이 돌아오면 안 된다").isTrue();
        Order after = reload(o.getId());
        assertThat(after.remainingCouponDiscount()).isEqualTo(2_858); // 5,000 − 2,142
        assertThat(after.getStatus()).isEqualTo(OrderStatus.ORDERED);
    }

    @Test
    @DisplayName("🔴 마지막 품목까지 빼면 주문이 CANCELLED 가 되고 **그때** 쿠폰이 돌아온다")
    void lastItemCancellation_cancelsOrderAndRestoresCoupon() throws Exception {
        Coupon coupon = couponRepository.save(Coupon.builder()
                .name(MARK + " 5천원").discountType(DiscountType.FIXED).discountValue(5_000L)
                .minOrderAmount(30_000L)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validUntil(Instant.now().plus(30, ChronoUnit.DAYS)).build());
        MemberCoupon mc = memberCouponRepository.save(MemberCoupon.issue(userId, coupon));
        mc.use();
        memberCouponRepository.flush();

        Order o = order(coupon.getName(), 5_000, mc.getId(), 0);
        String token = login(userLoginId);
        cancelItem("/cancel-item", o.getId(), token, itemId(o, 1), 1).andExpect(status().isOk());
        cancelItem("/cancel-item", o.getId(), token, itemId(o, 0), 1).andExpect(status().isOk());

        Order after = reload(o.getId());
        assertThat(after.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(after.getPayAmount()).isZero();
        assertThat(after.refundedAmount()).isEqualTo(30_000); // 35,000 − 쿠폰 5,000
        assertThat(memberCouponRepository.findById(mc.getId()).orElseThrow().isUsed())
                .as("전량 취소되면 쿠폰은 돌아와야 한다").isFalse();
    }

    // ────────────────────────── 재고 · 적립금 ──────────────────────────

    @Test
    @DisplayName("재고는 **취소한 수량만큼만** 돌아온다")
    void restoresStockForCancelledQuantityOnly() throws Exception {
        Order o = order(null, 0, null, 0);
        // 🔴 **`findById` 로 읽으면 안 된다.** `increaseStock` 은 `@Modifying` 벌크 UPDATE 라
        //    1차 캐시를 안 건드린다 — 미리 읽어 둔 엔티티를 다시 읽으면 **옛 값이 그대로** 나온다.
        //    ⚠ 2026-08-24 에 이 테스트가 그렇게 한 번 빨개졌다(기대 6, 실제 5 — 복원은 실제로 됐다).
        //    `ProductVariantRepository` javadoc 이 «벌크 UPDATE 뒤 stale» 을 경고하는 그 자리이고,
        //    본코드도 같은 이유로 스칼라 프로젝션(`findStockSnapshot`·`sumStockByProduct`)을 쓴다.
        long before = variantRepository.sumStockByProduct(productId);

        cancelItem("/cancel-item", o.getId(), login(userLoginId), itemId(o, 1), 1).andExpect(status().isOk());

        assertThat(variantRepository.sumStockByProduct(productId)).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("두 품목을 다 빼면 재고도 둘 다 돌아온다 — 수량이 겹쳐 세어지지 않는다")
    void restoresStockOncePerCancellation() throws Exception {
        Order o = order(null, 0, null, 0);
        long before = variantRepository.sumStockByProduct(productId);
        String token = login(userLoginId);

        cancelItem("/cancel-item", o.getId(), token, itemId(o, 1), 1).andExpect(status().isOk());
        cancelItem("/cancel-item", o.getId(), token, itemId(o, 0), 1).andExpect(status().isOk());

        // 🔴 마지막 취소로 주문이 CANCELLED 가 되는데, 그때 applyCancellation 을 또 부르면
        //    재고가 **두 번** 돌아온다(+4 가 된다). 여기가 그 자리를 막는다.
        assertThat(variantRepository.sumStockByProduct(productId)).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("🔴 쓴 적립금이 **몫만큼** 계정으로 돌아온다")
    void refundsPointShare() throws Exception {
        Order o = order(null, 0, null, 2_000);
        long before = pointService.balanceOf(userId);

        // B(15,000) 취소 → 적립금 몫 2000 × 15000/35000 = 857.1 → 857
        cancelItem("/cancel-item", o.getId(), login(userLoginId), itemId(o, 1), 1).andExpect(status().isOk());

        assertThat(pointService.balanceOf(userId)).isEqualTo(before + 857);
        assertThat(reload(o.getId()).getCancelledPoint()).isEqualTo(857);
    }

    // ────────────── 🔴 감사 — Oracle enum CHECK 트랩이 여기서 드러난다 ──────────────

    @Test
    @DisplayName("🔴 관리자 부분 취소가 원장에 ORDER_ITEM_CANCEL 로 남는다 — V57 이 CHECK 를 안 넓혔으면 ORA-02290")
    void adminPartialCancel_writesAudit() throws Exception {
        Order o = order(null, 0, null, 0);
        long before = auditLogRepository.count();

        cancelItem("/admin-cancel-item", o.getId(), login(adminLoginId), itemId(o, 1), 1)
                .andExpect(status().isOk());
        auditLogRepository.flush(); // ⚠ flush 해야 CHECK 가 실제로 밟힌다 — 안 하면 커밋까지 미뤄진다

        assertThat(auditLogRepository.count()).isEqualTo(before + 1);
        var log = auditLogRepository.findAll().stream()
                .filter(a -> a.getAction() == AuditAction.ORDER_ITEM_CANCEL)
                .filter(a -> a.getDetail() != null && a.getDetail().contains(o.getOrderNo()))
                .findFirst().orElseThrow();
        // 「무엇을 몇 개 빼고 얼마를 돌려줬나」 — AuditAction 주석이 정한 내용이다.
        assertThat(log.getDetail()).contains(MARK + "-B").contains("1개").contains("15000원");
    }

    @Test
    @DisplayName("전체 취소와 **다른 행동**으로 남는다 — 돈이 다르게 움직이므로 원장에서 갈려야 한다")
    void partialAndFullCancelAreDifferentActions() throws Exception {
        Order o = order(null, 0, null, 0);
        cancelItem("/admin-cancel-item", o.getId(), login(adminLoginId), itemId(o, 1), 1)
                .andExpect(status().isOk());
        auditLogRepository.flush();

        assertThat(auditLogRepository.findAll().stream()
                .filter(a -> a.getDetail() != null && a.getDetail().contains(o.getOrderNo()))
                .map(a -> a.getAction()))
                .containsOnly(AuditAction.ORDER_ITEM_CANCEL)
                .doesNotContain(AuditAction.ORDER_CANCEL);
    }

    // ────────────────────────── 거절되는 경우 ──────────────────────────

    @Test
    @DisplayName("남은 수량을 넘기면 400 — 주문은 그대로여야 한다")
    void tooManyRejected() throws Exception {
        Order o = order(null, 0, null, 0);
        cancelItem("/cancel-item", o.getId(), login(userLoginId), itemId(o, 1), 2)
                .andExpect(status().isBadRequest());
        assertThat(reload(o.getId()).getCancelledItemsTotal()).isZero();
    }

    @Test
    @DisplayName("0개·음수는 400 — 왕복할 이유가 없다")
    void nonPositiveRejected() throws Exception {
        Order o = order(null, 0, null, 0);
        String token = login(userLoginId);
        cancelItem("/cancel-item", o.getId(), token, itemId(o, 1), 0).andExpect(status().isBadRequest());
        cancelItem("/cancel-item", o.getId(), token, itemId(o, 1), -1).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("그 주문에 없는 품목이면 404")
    void unknownItemRejected() throws Exception {
        Order o = order(null, 0, null, 0);
        cancelItem("/cancel-item", o.getId(), login(userLoginId), UUID.randomUUID(), 1)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("🔴 발송된 주문은 부분 취소도 못 한다 — 전체 취소와 같은 규칙이다(회수 절차는 반품이 맡는다)")
    void shippedOrderRejected() throws Exception {
        Order o = order(null, 0, null, 0);
        o.pay();
        o.ship(com.glassvue.domain.order.entity.DeliveryCarrier.CJ, "123456789");
        orderRepository.saveAndFlush(o);

        cancelItem("/cancel-item", o.getId(), login(userLoginId), itemId(o, 1), 1)
                .andExpect(status().isBadRequest());
    }

    // ────────────────────────── 응답 계약 ──────────────────────────

    @Test
    @DisplayName("🔴 응답의 payAmount 는 **지금 받을 금액**이고, 원본 스냅샷은 안 변한다")
    void responseSeparatesSnapshotFromNow() throws Exception {
        Order o = order(MARK + " 쿠폰", 5_000, null, 2_000);
        String token = login(userLoginId);
        cancelItem("/cancel-item", o.getId(), token, itemId(o, 1), 1).andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/orders/" + o.getId()).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPrice").value(35_000))      // 원본 스냅샷
                .andExpect(jsonPath("$.data.couponDiscount").value(5_000))   // 원본 스냅샷
                .andExpect(jsonPath("$.data.usedPoint").value(2_000))        // 원본 스냅샷
                .andExpect(jsonPath("$.data.cancelledItemsTotal").value(15_000))
                .andExpect(jsonPath("$.data.refundedAmount").value(12_001))
                .andExpect(jsonPath("$.data.cancelledPoint").value(857))
                .andExpect(jsonPath("$.data.payAmount").value(15_999));      // 지금 받을 금액
    }

    @Test
    @DisplayName("품목 응답이 원래 수량과 남은 수량을 **둘 다** 준다 — 「3개 중 1개 취소됨」을 그리려면 필요하다")
    void itemResponseCarriesBothQuantities() throws Exception {
        Order o = order(null, 0, null, 0);
        String token = login(userLoginId);
        cancelItem("/cancel-item", o.getId(), token, itemId(o, 1), 1).andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/orders/" + o.getId()).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.productName=='" + MARK + "-B')].quantity").value(1))
                .andExpect(jsonPath("$.data.items[?(@.productName=='" + MARK + "-B')].cancelledQuantity").value(1))
                .andExpect(jsonPath("$.data.items[?(@.productName=='" + MARK + "-B')].remainingQuantity").value(0))
                .andExpect(jsonPath("$.data.items[?(@.productName=='" + MARK + "-A')].remainingQuantity").value(1));
    }
}
