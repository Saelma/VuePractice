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
import com.glassvue.domain.cart.service.CartService;
import com.glassvue.domain.point.entity.PointAccount;
import com.glassvue.domain.point.repository.PointAccountRepository;
import com.glassvue.domain.point.repository.PointHistoryRepository;
import com.glassvue.domain.point.service.PointService;
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
 * 적립금 · 회원 등급 (2026-07-24, 백로그 C-10).
 *
 * <p>여기서만 드러나는 것:
 * <ol>
 *   <li><b>잔액 == 이력의 합</b> — 이력이 원장이고 잔액은 캐시다. 잔액만 고치고 이력을 빠뜨리는
 *       코드를 잡는 유일한 장치라 <b>거의 모든 테스트가 마지막에 이걸 확인</b>한다.</li>
 *   <li><b>적립 기준액</b> — 배송비·쿠폰·사용 적립금을 뺀 "실제로 낸 상품 대금"인가.
 *       특히 적립금으로 낸 부분에 적립이 붙으면 <b>포인트가 포인트를 낳는다.</b></li>
 *   <li><b>배송완료 전에는 적립이 없다</b> — 결제만으로 주면 취소 시 회수해야 한다.</li>
 *   <li>🔴 <b>등급의 두 번째 효과 — 무료배송 기준 인하</b>(2026-08-28, BACKLOG G-6). 등급이
 *       {@code MemberGrade.discountedThreshold} 로 «적용할 기준 금액» 을 내고 장바구니·주문이 그걸 쓴다.
 *       ⚠ <b>단위 테스트로는 «둘이 서로 안 맞는» 자리를 못 본다</b> — {@code feeFor} 와
 *       {@code amountUntilFree} 는 각각 맞는 값을 돌려주고 <b>서로 어긋날</b> 뿐이라,
 *       한 응답 안에서 둘을 함께 읽는 여기서만 드러난다(2026-09-01 추가).</li>
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
    /** 장바구니는 Redis 라 롤백이 안 닿는다 — {@code @AfterEach} 로 직접 비운다. */
    @Autowired CartService cartService;

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
    /** 🔴 <b>적립금 계정이 없는</b> 회원 — {@code gradeOf} 의 «계정 없으면 BRONZE» 갈래를 밟는 표본(G-6). */
    private String noAccountLoginId;
    private UUID noAccountId;
    /** 5,000원짜리 — 무료배송 기준(30,000 / SILVER 24,000) 근처를 <b>1,000원 단위로</b> 짚기 위한 상품. */
    private UUID shipVariantId;

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

        // G-6 — 배송비 경계를 짚을 싼 상품. 50,000 짜리로는 25,000 짜리 장바구니를 만들 수 없다.
        noAccountLoginId = "ptna_" + suffix;
        noAccountId = member(noAccountLoginId, "ZZ무계정" + suffix, Role.USER);
        // ⚠ 여기서 openAccount 를 부르지 않는다 — «계정이 없는 회원» 이 이 표본의 전부다.
        UUID shipProductId = productRepository.save(Product.builder()
                .name("ZZP-배송비상품" + suffix).description("d").price(5_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
        shipVariantId = variantRepository.save(ProductVariant.of(shipProductId, "기본", 0, 1000, 0)).getId();
    }

    /**
     * ⚠ 장바구니는 <b>Redis</b> 라 {@code @Transactional} 롤백이 안 닿는다(WA §3 — 외부 자원은 스스로 치운다).
     * 아래 G-6 테스트들은 결제까지 가지 않아 장바구니가 안 비워진다.
     */
    @AfterEach
    void tearDown() {
        cartService.clear(buyerId);
        cartService.clear(noAccountId);
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
                                + "\",\"nickname\":\"ZZ신규" + loginId.substring(6) + "\",\"email\":\"" + loginId + "@example.com\",\"agreeTerms\":true}"))
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
    @DisplayName("🔴 주문을 취소하면 쓴 적립금이 돌아온다 — 안 돌려주면 고객 돈이 사라진다")
    void refundUsedPointOnCancel() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        deliverFully(buyer, admin, order(buyer, 1, null));   // 500 적립

        String orderId = order(buyer, 1, 500L);              // 500 을 써서 잔액 0
        mockMvc.perform(get("/api/points/me").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.balance").value(0));

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", buyer)
                        .contentType(JSON).content("{\"reason\":\"ZZ변심\"}"))
                .andExpect(status().isOk());

        // 취소는 ORDERED·PAID 에서만 되고, 적립금 차감은 **주문 시점**에 이미 끝나 있다.
        // 그러니 취소가 되돌리지 않으면 그 500 은 어디로도 안 간다 — 화면은 «취소됨» 으로 멀쩡하고
        // 알림도 정상이라, 고객이 잔액을 들여다보기 전까지 아무도 모른다.
        mockMvc.perform(get("/api/points/me").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.balance").value(500));
        assertLedgerConsistent();
    }

    @Test
    @DisplayName("⚠ 적립금을 안 쓴 주문을 취소하면 이력이 **안 생긴다** — 0원 줄로 원장을 채우지 않는다")
    void noHistoryWhenCancellingOrderWithoutPoint() throws Exception {
        String buyer = login(buyerLoginId);
        String orderId = order(buyer, 1, null);              // 적립금 안 씀

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", buyer)
                        .contentType(JSON).content("{\"reason\":\"ZZ변심\"}"))
                .andExpect(status().isOk());

        // ⚠ 잔액 합계로는 이걸 못 잡는다 — 0원 이력은 합을 안 바꾼다(assertLedgerConsistent 가 통과한다).
        //    원장에 줄이 생겼는지는 **줄을 세어야** 알 수 있다.
        assertThat(historyRepository.findByOrderId(UUID.fromString(orderId)))
                .as("적립금이 안 움직였으면 원장에 줄이 없어야 한다")
                .isEmpty();
        assertLedgerConsistent();
    }

    @Test
    @DisplayName("🔴 취소는 **등급·누적 구매액을 안 건드린다** — 안 더한 것을 빼면 강등된다")
    void cancelDoesNotTouchGrade() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        deliverFully(buyer, admin, order(buyer, 1, null));   // 누적 50,000 · 500 적립

        String orderId = order(buyer, 1, 500L);
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", buyer)
                        .contentType(JSON).content("{\"reason\":\"ZZ변심\"}"))
                .andExpect(status().isOk());

        // 누적 구매확정액은 **배송완료에만** 오른다. 취소가 subtractPurchase 를 부르면
        // 이 주문이 더한 적 없는 49,500 을 빼서 누적이 500 으로 주저앉는다 — 등급도 함께 틀어진다.
        // ⚠ 그런데도 **잔액은 맞고 이력 합도 맞는다.** 그래서 여기서만 잡힌다.
        mockMvc.perform(get("/api/points/me").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.totalPurchase").value(50_000))
                .andExpect(jsonPath("$.data.balance").value(500));
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

    /* ═════════ 등급별 무료배송 (2026-08-28, BACKLOG G-6 · 통합은 2026-09-01 에 붙였다) ═════════
     *
     * 🔴 **왜 뒤늦게 붙었나**: G-6 은 단위 테스트 여섯 벌로 커밋·배포됐고 **HTTP 통합은 0건**이었다.
     *   그런데 그날 전수는 통합 488건을 통째로 건너뛴 상태였다(08-28 §4) — 즉 «없는 것» 과
     *   «있는데 안 돈 것» 이 겹쳐 있었다. 09-01 에 브라우저로 밟아 통과를 봤지만,
     *   **화면으로 본 값을 내일 지켜 주는 것이 없었다.** 아래 숫자가 그날 화면에서 읽은 값 그대로다.
     *
     * ⚠ **`OrderFlowIntegrationTest` 가 이미 배송비를 단언하는데 왜 또 쓰나**: 그 테스트의 회원은
     *   적립금 계정이 없어 `gradeOf` 의 **BRONZE 갈래**로 흐른다 — 기준이 30,000 그대로라
     *   **G-6 을 넣기 전과 후가 똑같이 초록이다.** 즉 그건 «아무 일도 안 일어나는» 쪽만 덮는다.
     */

    /** 누적구매를 올려 SILVER 로 만든다 — 픽스처는 <b>두 줄</b>이다(공개 메서드라 반사도 SQL 도 필요 없다). */
    private void makeSilver() {
        PointAccount account = accountRepository.findByMemberId(buyerId).orElseThrow();
        account.addPurchase(200_000);              // 100,000 이상 → SILVER
        accountRepository.saveAndFlush(account);
    }

    private void addToCart(String auth, UUID variant, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items").header("Authorization", auth).contentType(JSON)
                        .content("{\"variantId\":\"" + variant + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("🔴 SILVER 는 무료배송 기준이 24,000 으로 내려간다 — 25,000 장바구니가 «무료» 다 (G-6)")
    void silverLowersFreeShippingThreshold() throws Exception {
        String buyer = login(buyerLoginId);
        makeSilver();

        // 기준 금액은 **서버가** 낸다 — 화면은 인하율(20%)도 기본 기준(30,000)도 모른다.
        mockMvc.perform(get("/api/points/me").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.grade").value("SILVER"))
                .andExpect(jsonPath("$.data.freeShippingThreshold").value(24_000));

        addToCart(buyer, shipVariantId, 5);                       // 5,000 × 5 = 25,000
        mockMvc.perform(get("/api/cart").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.totalPrice").value(25_000))
                // BRONZE 기준(30,000)이면 여기서 3,000 이 붙는다 — 그게 «등급이 먹었나» 를 가르는 지점이다.
                .andExpect(jsonPath("$.data.shippingFee").value(0))
                .andExpect(jsonPath("$.data.amountUntilFree").value(0))
                .andExpect(jsonPath("$.data.payAmount").value(25_000));
    }

    @Test
    @DisplayName("🔴 남은 금액도 등급 기준을 쓴다 — SILVER 20,000 은 «4,000원 더» 다 (G-6, feeFor ↔ amountUntilFree)")
    void silverRemainingUsesSameThresholdAsFee() throws Exception {
        String buyer = login(buyerLoginId);
        makeSilver();

        addToCart(buyer, shipVariantId, 4);                       // 5,000 × 4 = 20,000
        mockMvc.perform(get("/api/cart").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.totalPrice").value(20_000))
                .andExpect(jsonPath("$.data.shippingFee").value(3_000))
                // 🔴 **여기가 이 파일의 핵심 단언이다.** 백로그 G-6 은 feeFor 만 말했고
                //    amountUntilFree 를 빠뜨렸다. 한쪽만 등급 기준을 쓰면 이 값이 **10,000** 이 되어
                //    화면이 «10,000원 더 담으면 무료배송» 이라 말한다 — 실제 기준은 24,000 인데.
                //    2026-09-01 브라우저 검증에서 읽은 값이 정확히 4,000 이었다.
                .andExpect(jsonPath("$.data.amountUntilFree").value(4_000))
                .andExpect(jsonPath("$.data.payAmount").value(23_000));
    }

    @Test
    @DisplayName("대조군 — BRONZE 는 그대로다: 같은 25,000 에 배송비 3,000 · 「5,000원 더」 (G-6)")
    void bronzeKeepsDefaultThreshold() throws Exception {
        String buyer = login(buyerLoginId);        // 계정은 있고 누적 0 → BRONZE

        mockMvc.perform(get("/api/points/me").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.grade").value("BRONZE"))
                .andExpect(jsonPath("$.data.freeShippingThreshold").value(30_000));

        addToCart(buyer, shipVariantId, 5);                       // 25,000
        mockMvc.perform(get("/api/cart").header("Authorization", buyer))
                // ⚠ **대조군이 없으면 판정이 반쪽이다** — 위 테스트만 보면 «원래 25,000 이면
                //    무료였던 것 아닌가» 를 못 가른다. 같은 금액에서 갈리는 것이 «등급이 원인» 의 증거다.
                .andExpect(jsonPath("$.data.shippingFee").value(3_000))
                .andExpect(jsonPath("$.data.amountUntilFree").value(5_000));
    }

    @Test
    @DisplayName("🔴 적립금 계정이 없어도 장바구니가 열린다 — BRONZE 로 보고 **계정을 만들지 않는다** (G-6)")
    void noAccountFallsBackToBronzeWithoutCreatingOne() throws Exception {
        String buyer = login(noAccountLoginId);
        assertThat(accountRepository.findByMemberId(noAccountId)).isEmpty();

        addToCart(buyer, shipVariantId, 5);                       // 25,000
        mockMvc.perform(get("/api/cart").header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shippingFee").value(3_000));

        // 🔴 **장바구니를 여는 것만으로 적립금 계정이 생기면 안 된다.** gradeOf 가 orElse(BRONZE) 로
        //    끝나고 save 를 안 하는 것이 그 약속이고, 그 약속은 **여기서만** 확인된다
        //    (단위 테스트는 목이라 «저장 안 했다» 를 목으로 다시 확인할 뿐이다).
        assertThat(accountRepository.findByMemberId(noAccountId))
                .as("장바구니 조회가 적립금 계정을 만들면 안 된다")
                .isEmpty();
    }

    @Test
    @DisplayName("등급 배송비는 주문에 **스냅샷**된다 — 나중에 등급이 내려가도 과거 주문은 안 바뀐다 (G-6)")
    void gradeShippingFeeIsSnapshotOnOrder() throws Exception {
        String buyer = login(buyerLoginId);
        makeSilver();
        addToCart(buyer, shipVariantId, 5);                       // 25,000 → SILVER 기준으로 무료

        String orderId = JsonPath.read(mockMvc.perform(post("/api/orders").header("Authorization", buyer)
                        .contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울시 강남구 테헤란로 1\",\"address2\":null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data");

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.shippingFee").value(0))
                .andExpect(jsonPath("$.data.payAmount").value(25_000));

        // 등급을 도로 내린다(반품·취소로 누적이 깎이면 실제로 일어난다).
        PointAccount account = accountRepository.findByMemberId(buyerId).orElseThrow();
        account.subtractPurchase(200_000);
        accountRepository.saveAndFlush(account);

        mockMvc.perform(get("/api/points/me").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.grade").value("BRONZE"))
                .andExpect(jsonPath("$.data.freeShippingThreshold").value(30_000));
        // 🔴 그래도 **과거 주문의 배송비는 그대로 0** 이다 — 부과된 금액은 기록이고 정책이 아니다
        //    (배송지·닉네임 스냅샷과 같은 규칙, G-4 결정 2 「무료배송 기준 소급 안 함」과 같은 방향).
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.shippingFee").value(0))
                .andExpect(jsonPath("$.data.payAmount").value(25_000));
    }
}
