package com.glassvue.domain.coupon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.cart.service.CartService;
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
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * 쿠폰이 <b>실제 주문에 적용되는</b> 경로 (H-7, 2026-07-31).
 *
 * <p>{@link CouponFlowIntegrationTest} 는 <b>생성·발급·미리보기</b>까지만 본다 —
 * 그래서 {@code CouponService.redeem} 은 H-5 실측에서 <b>0%</b>, 즉 쿠폰을 실제로 쓰는 경로가
 * 한 번도 실행된 적이 없었다. 단위({@code CouponServiceTest})가 가드를 보고, 여기서는
 * <b>주문 트랜잭션과 함께 도는지</b>를 본다 — 둘은 대체재가 아니다:
 *
 * <ul>
 *   <li>할인액이 <b>주문 금액에 실제로 반영</b>되고 쿠폰명이 스냅샷으로 남는가
 *       (계산은 맞는데 주문에 안 실리면 단위 테스트는 통과한다).</li>
 *   <li>같은 쿠폰으로 <b>두 번째 주문이 막히는가</b> — 사용 처리가 커밋돼야만 막힌다.</li>
 *   <li>남의 쿠폰이 <b>"없는 것"</b> 으로 답하는가(열거 방지가 HTTP 계층까지 유지되는가).</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CouponOrderIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CartService cartService;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final String SHIPPING = "\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\","
            + "\"zipcode\":\"06134\",\"address1\":\"서울시 강남구 테헤란로 1\",\"address2\":\"3층\"";

    private String buyerLoginId;
    private String otherLoginId;
    private String adminLoginId;
    private UUID buyerId;
    private UUID otherId;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        buyerLoginId = "cobuy_" + suffix;
        otherLoginId = "cooth_" + suffix;
        adminLoginId = "coadm_" + suffix;
        buyerId = member(buyerLoginId, "ZZ쿠폰구매자", Role.USER);
        otherId = member(otherLoginId, "ZZ남의계정", Role.USER);
        member(adminLoginId, "ZZ쿠폰관리자", Role.ADMIN);

        Category cat = categoryRepository.save(Category.builder().name("ZZC-쿠폰주문" + suffix).build());
        Product p = productRepository.save(Product.builder()
                .name("ZZP-쿠폰상품" + suffix).description("d").price(10_000)
                .status(ProductStatus.SELLING).category(cat).build());
        variantId = variantRepository.save(ProductVariant.of(p.getId(), "기본", 0, 100, 0)).getId();
    }

    /**
     * ⚠ 장바구니는 <b>Redis</b> 라 {@code @Transactional} 롤백이 안 닿는다(WA §3 — 외부 자원은 스스로 치운다).
     * 실패하는 결제는 장바구니를 비우지 않으므로 여기서 직접 지운다.
     */
    @AfterEach
    void tearDown() {
        cartService.clear(buyerId);
        cartService.clear(otherId);
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

    /** 관리자가 정액 5,000원(최소주문 10,000) 쿠폰을 만들어 회원에게 발급하고, 그 회원의 보유 쿠폰 id 를 돌려준다. */
    private String issueCouponTo(String adminToken, String ownerToken, UUID ownerId) throws Exception {
        mockMvc.perform(post("/api/admin/coupons").header("Authorization", adminToken).contentType(JSON)
                        .content("{\"name\":\"ZZ 주문쿠폰\",\"discountType\":\"FIXED\",\"discountValue\":5000,"
                               + "\"minOrderAmount\":10000,"
                               + "\"validFrom\":\"2026-01-01T00:00:00Z\",\"validUntil\":\"2027-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String couponId = JsonPath.read(mockMvc.perform(get("/api/admin/coupons").header("Authorization", adminToken))
                .andReturn().getResponse().getContentAsString(), "$.data.content[0].id");

        mockMvc.perform(post("/api/admin/coupons/" + couponId + "/issue?memberId=" + ownerId)
                        .header("Authorization", adminToken)).andExpect(status().isOk());

        String mine = mockMvc.perform(get("/api/coupons/me?itemsTotal=20000").header("Authorization", ownerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(mine, "$.data[0].id");
    }

    private void fillCart(String token) throws Exception {
        mockMvc.perform(post("/api/cart/items").header("Authorization", token).contentType(JSON)
                        .content("{\"variantId\":\"" + variantId + "\",\"quantity\":2}"))   // 10,000 × 2
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions checkout(String token, String memberCouponId)
            throws Exception {
        return mockMvc.perform(post("/api/orders").header("Authorization", token).contentType(JSON)
                .content("{" + SHIPPING + ",\"memberCouponId\":\"" + memberCouponId + "\"}"));
    }

    @Test
    @DisplayName("쿠폰으로 주문 → 할인이 주문 금액에 실리고 쿠폰명이 스냅샷으로 남는다")
    void couponAppliesToOrder() throws Exception {
        String admin = login(adminLoginId);
        String buyer = login(buyerLoginId);
        String couponId = issueCouponTo(admin, buyer, buyerId);
        fillCart(buyer);

        String orderId = JsonPath.read(checkout(buyer, couponId)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data");

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPrice").value(20_000))
                .andExpect(jsonPath("$.data.couponDiscount").value(5_000))
                // 이름은 주문 시점 **스냅샷** — 나중에 쿠폰 정의가 바뀌어도 주문서는 안 흔들린다(V17).
                .andExpect(jsonPath("$.data.couponName").value("ZZ 주문쿠폰"))
                // 20,000 − 5,000 + 배송비 3,000
                .andExpect(jsonPath("$.data.payAmount").value(18_000));
    }

    @Test
    @DisplayName("⚠ 같은 쿠폰으로 두 번째 주문은 409 — 사용 처리가 주문과 함께 커밋돼야만 막힌다")
    void sameCouponCannotBeUsedTwice() throws Exception {
        String admin = login(adminLoginId);
        String buyer = login(buyerLoginId);
        String couponId = issueCouponTo(admin, buyer, buyerId);

        fillCart(buyer);
        checkout(buyer, couponId).andExpect(status().isCreated());

        fillCart(buyer);   // 결제가 장바구니를 비우므로 다시 담는다
        checkout(buyer, couponId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COUPON-409"));
    }

    @Test
    @DisplayName("⚠ 남의 쿠폰으로는 주문할 수 없다 — **404(없는 것)** 로 답해 존재를 알려주지 않는다")
    void strangersCouponIsRejectedAsMissing() throws Exception {
        String admin = login(adminLoginId);
        String buyer = login(buyerLoginId);
        String other = login(otherLoginId);
        String buyersCoupon = issueCouponTo(admin, buyer, buyerId);   // 발급 대상은 buyer

        fillCart(other);
        checkout(other, buyersCoupon)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COUPON-404"));
    }
}
