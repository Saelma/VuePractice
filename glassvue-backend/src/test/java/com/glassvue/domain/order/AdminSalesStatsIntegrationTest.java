package com.glassvue.domain.order;

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
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
 * 관리자 매출 통계 (2026-07-24, 백로그 C-11).
 *
 * <p>여기서만 드러나는 것 셋:
 * <ol>
 *   <li><b>매출의 정의</b> — 미결제(ORDERED)·취소(CANCELLED)가 섞이지 않는가.
 *       특히 <b>결제 후 취소</b>는 {@code paid_at} 이 남아 있어서 시각으로만 거르면 매출에 섞인다.</li>
 *   <li><b>KST 경계</b> — UTC 로 자르면 한국 시간 00:00~09:00 결제가 <b>전날</b>로 찍힌다.
 *       이걸 잡으려면 {@code paid_at} 을 그 구간에 <b>직접 박아 넣어야</b> 한다(아래 {@code forcePaidAt}).</li>
 *   <li><b>권한</b> — 매출은 새 나가면 안 되는 정보다. 401·403 을 계약으로 고정한다.</li>
 * </ol>
 *
 * <p>기존 espdb 에는 다른 주문이 이미 쌓여 있으므로 <b>절대값을 단정하지 않는다.</b>
 * 대신 이 테스트가 만든 주문의 <b>증분</b>을 확인한다 — 그래야 실행 시점·데이터와 무관하게 통과한다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSalesStatsIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired EntityManager entityManager;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final String URL = "/api/admin/stats/sales";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private String buyerLoginId;
    private String adminLoginId;
    private UUID productId;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        buyerLoginId = "stbuyer_" + suffix;
        adminLoginId = "stadmin_" + suffix;
        member(buyerLoginId, "ZZ통계구매자" + suffix, Role.USER);
        member(adminLoginId, "ZZ통계관리자" + suffix, Role.ADMIN);

        Category cat = categoryRepository.save(Category.builder().name("ZZC-통계" + suffix).build());
        productId = productRepository.save(Product.builder()
                .name("ZZP-통계상품" + suffix).description("d").price(10_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
        variantId = variantRepository.save(ProductVariant.of(productId, "기본", 0, 1000, 0)).getId();
    }

    private void member(String loginId, String nickname, Role role) {
        memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW)).nickname(nickname).role(role).build());
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /** 장바구니 담기 → 주문 생성. 수량 1건이면 상품합계 10,000 + 배송비 3,000 이다. */
    private String order(String buyer, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items").header("Authorization", buyer).contentType(JSON)
                .content("{\"variantId\":\"" + variantId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/orders").header("Authorization", buyer).contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울시 강남구 테헤란로 1\",\"address2\":null}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data");
    }

    private void pay(String buyer, String orderId) throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", buyer))
                .andExpect(status().isOk());
    }

    /**
     * {@code paid_at} 을 특정 시각으로 박는다.
     *
     * <p>정상 경로로는 "지금" 밖에 만들 수 없어서 <b>KST 경계를 재현할 방법이 없다.</b>
     * 그래서 테스트에서만 직접 쓴다. 운영 코드에는 이런 경로가 없다.
     */
    /** 발송·배송완료(관리자). 반품은 배송완료 주문에서만 요청할 수 있어 여기까지 밀어 준다. */
    private void ship(String admin, String orderId) throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/ship").header("Authorization", admin)
                        .contentType(JSON).content("{\"carrier\":\"CJ\",\"trackingNo\":\"123456789012\"}"))
                .andExpect(status().isOk());
    }

    private void deliver(String admin, String orderId) throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/deliver").header("Authorization", admin))
                .andExpect(status().isOk());
    }

    private void forcePaidAt(String orderId, Instant paidAt) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE orders SET paid_at = ?1 WHERE id = ?2")
                .setParameter(1, paidAt)
                .setParameter(2, uuidToBytes(UUID.fromString(orderId)))
                .executeUpdate();
        entityManager.clear();
    }

    private static byte[] uuidToBytes(UUID uuid) {
        return java.nio.ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    private long allTimeItemSales(String admin) throws Exception {
        String body = mockMvc.perform(get(URL).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.allTime.itemSales")).longValue();
    }

    @Test
    @DisplayName("미결제(ORDERED) 주문은 매출이 아니다 — 돈이 안 들어왔다")
    void unpaidIsNotRevenue() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);

        long before = allTimeItemSales(admin);
        order(buyer, 1);   // 결제하지 않는다
        assertItemSalesDelta(admin, before, 0);
    }

    @Test
    @DisplayName("결제하면 상품매출과 배송비가 **따로** 잡힌다 (합치지 않는다)")
    void paidCountsItemsAndShippingSeparately() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);

        String body = mockMvc.perform(get(URL).header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        long itemsBefore = ((Number) JsonPath.read(body, "$.data.allTime.itemSales")).longValue();
        long shipBefore = ((Number) JsonPath.read(body, "$.data.allTime.shippingSales")).longValue();

        pay(buyer, order(buyer, 1));   // 상품 10,000 + 배송비 3,000

        mockMvc.perform(get(URL).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allTime.itemSales").value(itemsBefore + 10_000))
                .andExpect(jsonPath("$.data.allTime.shippingSales").value(shipBefore + 3_000));
    }

    @Test
    @DisplayName("⚠ 결제 후 취소된 주문은 매출에서 빠진다 — paid_at 이 남아 있어 시각으로만 거르면 섞인다")
    void paidThenCancelledIsExcluded() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);

        long before = allTimeItemSales(admin);
        String orderId = order(buyer, 1);
        pay(buyer, orderId);
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", buyer))
                .andExpect(status().isOk());

        // paid_at 은 그대로 남아 있다. 그래도 매출이 아니어야 한다(환불).
        assertItemSalesDelta(admin, before, 0);
    }

    /**
     * 🔴 <b>반품 「요청」만으로는 매출이 안 빠진다</b> (2026-08-11, 08-10 §16-4 8번).
     *
     * <p>고치기 전에는 {@code RETURN_REQUESTED} 가 매출 상태 목록에 없어 <b>요청하는 순간 빠졌다.</b>
     * 두 가지가 어긋났다:
     * <ul>
     *   <li>{@code sold_count} 는 <b>승인</b>에만 반응해서({@code SalesEventListener}) 시점이 달랐다.</li>
     *   <li>🔴 거절하면 {@code DELIVERED} 로 돌아가 금액이 <b>다시 잡힌다</b> —
     *       <b>과거 날짜의 일별 매출이 나중에 바뀐다.</b></li>
     * </ul>
     * 실측(고치기 전): 매출 347,000원(14건) ↔ 요청 포함 355,000원(15건).
     */
    @Test
    @DisplayName("🔴 반품 **요청**은 매출에 남는다 — 승인되어야 확정이고, 거절되면 되살아나 과거 매출이 바뀐다")
    void returnRequestedStaysInRevenue() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);

        long before = allTimeItemSales(admin);
        String orderId = order(buyer, 1);
        pay(buyer, orderId);
        long afterPaid = allTimeItemSales(admin);
        // 전제 — 결제로 매출이 실제로 늘었어야 «안 빠졌다» 가 뜻을 갖는다(WA §3-3).
        org.assertj.core.api.Assertions.assertThat(afterPaid)
                .as("결제가 매출에 안 잡혔다면 아래 단언은 아무것도 증명하지 않는다")
                .isGreaterThan(before);

        ship(admin, orderId);
        deliver(admin, orderId);
        mockMvc.perform(post("/api/orders/" + orderId + "/return-request")
                        .header("Authorization", buyer).contentType(JSON)
                        .content(fullReturnBody(buyer, orderId, "ZZ-반품요청")))
                .andExpect(status().isOk());

        // 🔴 요청만으로는 매출이 그대로여야 한다.
        org.assertj.core.api.Assertions.assertThat(allTimeItemSales(admin))
                .as("반품 요청은 아직 확정이 아니다 — 빼면 거절 시 되살아나 과거 매출이 바뀐다")
                .isEqualTo(afterPaid);
    }

    /**
     * 대조군 — <b>승인</b>하면 빠진다. 위 테스트만 있으면 «반품은 매출에서 안 뺀다» 로 고쳐도 통과한다.
     */
    @Test
    @DisplayName("대조군: 반품 **승인**은 매출에서 뺀다 (돈을 돌려줬으니)")
    void returnApprovedIsExcluded() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);

        long before = allTimeItemSales(admin);
        String orderId = order(buyer, 1);
        pay(buyer, orderId);
        ship(admin, orderId);
        deliver(admin, orderId);
        mockMvc.perform(post("/api/orders/" + orderId + "/return-request")
                        .header("Authorization", buyer).contentType(JSON)
                        .content(fullReturnBody(buyer, orderId, "ZZ-반품요청")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + orderId + "/return-approve").header("Authorization", admin))
                .andExpect(status().isOk());

        assertItemSalesDelta(admin, before, 0);
    }

    @Test
    @DisplayName("⚠ KST 경계 — 한국 시간 새벽 결제가 **그날**로 잡힌다 (UTC로 자르면 전날이 된다)")
    void kstBoundary() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);

        LocalDate todayKst = LocalDate.now(KST);
        String today = todayKst.format(DAY);
        String yesterday = todayKst.minusDays(1).format(DAY);

        // ⚠ 공유 espdb 에는 어제·그제 매출이 이미 쌓여 있다. 절대값이 아니라 **증분**을 본다.
        long todayBefore = dailyItemSales(admin, today);
        long yesterdayBefore = dailyItemSales(admin, yesterday);

        String orderId = order(buyer, 1);
        pay(buyer, orderId);
        // 오늘 KST 01:00 = 어제 UTC 16:00. UTC 기준으로 자르면 **어제**로 찍힌다.
        forcePaidAt(orderId, todayKst.atStartOfDay(KST).plusHours(1).toInstant());

        // 이 주문(10,000)은 **오늘**에만 더해져야 한다. 어제가 늘었다면 KST 변환이 빠진 것이다.
        org.assertj.core.api.Assertions.assertThat(dailyItemSales(admin, today))
                .as("KST 새벽 결제는 그날에 잡혀야 한다")
                .isEqualTo(todayBefore + 10_000);
        org.assertj.core.api.Assertions.assertThat(dailyItemSales(admin, yesterday))
                .as("어제는 변하면 안 된다 — 변했다면 UTC 로 잘린 것이다")
                .isEqualTo(yesterdayBefore);
    }

    /** 일별 배열에서 특정 날짜 칸의 상품매출. 빈 날도 채워져 오므로 항상 한 건이 잡힌다. */
    private long dailyItemSales(String admin, String date) throws Exception {
        String body = mockMvc.perform(get(URL).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<Object> hit =
                JsonPath.read(body, "$.data.daily[?(@.date == '" + date + "')].itemSales");
        return ((Number) hit.get(0)).longValue();
    }

    @Test
    @DisplayName("일별 추이는 매출이 0인 날도 채워서 30칸으로 준다 — 차트에 구멍이 안 생기게")
    void dailyIsFilled() throws Exception {
        String admin = login(adminLoginId);
        String today = LocalDate.now(KST).format(DAY);

        mockMvc.perform(get(URL).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daily.length()").value(30))
                // 마지막 칸이 오늘, 날짜는 오름차순
                .andExpect(jsonPath("$.data.daily[29].date").value(today));
    }

    @Test
    @DisplayName("상품별 TOP 에 판매 수량이 집계된다 (쿠폰 할인 전 금액)")
    void topProducts() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);

        pay(buyer, order(buyer, 3));   // 3개 × 10,000 = 30,000

        String body = mockMvc.perform(get(URL).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        java.util.List<Object> qty = JsonPath.read(body,
                "$.data.topProducts[?(@.productId == '" + productId + "')].quantity");
        java.util.List<Object> sales = JsonPath.read(body,
                "$.data.topProducts[?(@.productId == '" + productId + "')].sales");
        org.assertj.core.api.Assertions.assertThat(((Number) qty.get(0)).longValue()).isEqualTo(3L);
        org.assertj.core.api.Assertions.assertThat(((Number) sales.get(0)).longValue()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("API 응답이 DB 직접 집계와 일치한다 — 정의가 코드와 SQL 양쪽에서 같은지")
    void matchesDirectAggregate() throws Exception {
        String admin = login(adminLoginId);
        pay(login(buyerLoginId), order(login(buyerLoginId), 2));   // 이 트랜잭션 안에서만 보이는 주문

        // 서비스가 쓰는 것과 **같은 정의**로 SQL 을 만든다. 둘이 갈라지면 여기서 잡힌다.
        //
        // 🔴 ⚠ **상태 목록을 손으로 적지 않는다**(2026-08-11). 예전엔 여기 `IN ('PAID','SHIPPED','DELIVERED')`
        //    라고 박아 뒀는데, 그게 **정의의 세 번째 복사본**이었다(서비스 상수 · 리포지토리 주석 · 여기).
        //    RETURN_REQUESTED 를 매출에 넣자 **이 테스트만 옛 정의로 남아** 15 ≠ 16 으로 깨졌다 —
        //    「코드와 SQL 이 같은지」를 본다면서 정작 **자기가 또 하나의 코드**였던 셈이다.
        // → 목록은 OrderStatus.isRevenue() 에서 받는다. 이 테스트가 지키는 것은 «어느 상태인가» 가 아니라
        //   **«같은 상태 집합으로 JPQL 과 native SQL 이 같은 답을 내는가»** 다(그게 원래 의도였다).
        String statusList = com.glassvue.domain.order.entity.OrderStatus.revenueStatusNames().stream()
                .map(s -> "'" + s + "'")
                .collect(java.util.stream.Collectors.joining(","));
        // 🔴 **부분 취소분을 뺀다**(2026-08-24, G-4). 상품매출은 원본이 아니라 «남은 것» 이다.
        //    ⚠ 이 식을 안 따라오면 위 주석이 말한 그 사고가 **또** 난다 — 지금은 부분 취소된 주문이
        //       전부 CANCELLED(매출 상태 아님)라 **우연히 통과**하지만, PAID 주문을 부분 취소하는
        //       순간 갈린다. 🔴 «우연히 맞는 것» 과 «맞는 것» 은 다르다.
        //    ⚠ 배송비는 안 뺀다 — 부분 취소로 움직이지 않는 값이다(G-4 결정 2).
        Object[] expected = (Object[]) entityManager.createNativeQuery("""
                SELECT COUNT(*),
                       NVL(SUM((o.total_price - o.cancelled_items_total)
                             - (o.coupon_discount - o.cancelled_coupon_discount)), 0),
                       NVL(SUM(o.shipping_fee), 0)
                  FROM orders o
                 WHERE o.status IN (%s)
                """.formatted(statusList)).getSingleResult();

        mockMvc.perform(get(URL).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allTime.orderCount").value(((Number) expected[0]).longValue()))
                .andExpect(jsonPath("$.data.allTime.itemSales").value(((Number) expected[1]).longValue()))
                .andExpect(jsonPath("$.data.allTime.shippingSales").value(((Number) expected[2]).longValue()));
    }

    // ---------------------------------------------------------------- 기간 선택 (B-26, 2026-08-13)

    /**
     * 🔴 <b>종료일은 포함이다</b> — 사람이 «7월 1~15일» 이라고 말할 때의 뜻.
     *
     * <p>⚠ 이걸 틀리면 <b>마지막 날 매출이 통째로 빠지고 아무도 눈치채지 못한다</b>(그 날 매출이
     * 0이면 표시가 안 나고, 0이 아니어도 «원래 그런가 보다» 로 읽힌다). 그래서 <b>마지막 날 23시</b>에
     * 결제를 박아 넣어 경계를 직접 밟는다.
     */
    @Test
    @DisplayName("종료일은 포함된다 — 마지막 날 23시 결제가 기간 안에 들어온다")
    void endDateIsInclusive() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        LocalDate todayKst = LocalDate.now(KST);

        String orderId = order(buyer, 1);
        pay(buyer, orderId);
        forcePaidAt(orderId, todayKst.atStartOfDay(KST).plusHours(23).toInstant());

        String range = "?from=" + todayKst.format(DAY) + "&to=" + todayKst.format(DAY);
        String body = mockMvc.perform(get(URL + range).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions
                .assertThat(((Number) JsonPath.read(body, "$.data.period.orderCount")).longValue())
                .as("마지막 날 23시 결제가 빠졌다면 종료일이 배타 경계로 처리된 것이다")
                .isGreaterThanOrEqualTo(1L);
    }

    /**
     * 기간 밖 주문은 안 잡힌다 — 위 테스트의 <b>대조군</b>.
     *
     * <p>⚠ 대조군이 없으면 «기간을 무시하고 전부 세는» 구현으로 고쳐도 위 테스트가 통과한다.
     */
    @Test
    @DisplayName("대조군: 기간 밖 주문은 요약·일별·TOP 어디에도 안 잡힌다")
    void outsidePeriodIsExcluded() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        LocalDate todayKst = LocalDate.now(KST);

        String orderId = order(buyer, 2);
        pay(buyer, orderId);
        // 20일 전으로 밀어 둔다.
        forcePaidAt(orderId, todayKst.minusDays(20).atStartOfDay(KST).plusHours(12).toInstant());

        // 최근 3일만 본다 — 위 주문은 밖이다.
        String range = "?from=" + todayKst.minusDays(2).format(DAY) + "&to=" + todayKst.format(DAY);
        String body = mockMvc.perform(get(URL + range).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        java.util.List<Object> qty = JsonPath.read(body,
                "$.data.topProducts[?(@.productId == '" + productId + "')].quantity");
        org.assertj.core.api.Assertions.assertThat(qty)
                .as("기간 밖 주문의 상품이 TOP 에 올라왔다 — topProducts 가 to 를 안 보는 것이다")
                .isEmpty();

        // 대조군의 대조군 — 그 날을 포함하면 잡혀야 한다(«아무것도 안 잡는» 구현이면 여기서 걸린다).
        String wide = "?from=" + todayKst.minusDays(25).format(DAY) + "&to=" + todayKst.format(DAY);
        String wideBody = mockMvc.perform(get(URL + wide).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<Object> wideQty = JsonPath.read(wideBody,
                "$.data.topProducts[?(@.productId == '" + productId + "')].quantity");
        org.assertj.core.api.Assertions.assertThat(wideQty).isNotEmpty();
    }

    @Test
    @DisplayName("일별 칸 수가 고른 기간을 따라간다 — 「최근 30일」 상수가 더 이상 답이 아니다")
    void dailyFollowsPeriodLength() throws Exception {
        String admin = login(adminLoginId);
        LocalDate todayKst = LocalDate.now(KST);
        String from = todayKst.minusDays(6).format(DAY);
        String to = todayKst.format(DAY);

        mockMvc.perform(get(URL + "?from=" + from + "&to=" + to).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daily.length()").value(7))
                .andExpect(jsonPath("$.data.daily[0].date").value(from))
                .andExpect(jsonPath("$.data.daily[6].date").value(to))
                // 서버가 **실제로 집계한 구간**을 되돌려 준다 — 화면 제목이 이 값을 쓴다.
                .andExpect(jsonPath("$.data.from").value(from))
                .andExpect(jsonPath("$.data.to").value(to));

        // 하루짜리도 한 칸이어야 한다(경계에서 0칸·2칸이 되기 쉬운 자리다).
        mockMvc.perform(get(URL + "?from=" + to + "&to=" + to).header("Authorization", admin))
                .andExpect(jsonPath("$.data.daily.length()").value(1));
    }

    /**
     * 🔴 <b>{@code today}·{@code thisMonth}·{@code allTime} 은 기간을 따라가면 안 된다.</b>
     *
     * <p>⚠ 「지난 달」을 골라 놓고 「오늘」 카드가 지난달 어느 날을 가리키면 <b>화면이 거짓말</b>을 한다.
     * ⚠ {@code thisMonth} 는 매출 화면이 안 그리지만 <b>관리자 홈이 읽는다</b> — B-26 에서 지우려다
     * 발견했다. 지웠으면 관리자 홈의 「이번 달」이 조용히 빈칸이 됐다.
     */
    @Test
    @DisplayName("today·thisMonth·allTime 은 기간과 무관하다 — 기간을 바꿔도 그대로다")
    void fixedSummariesIgnorePeriod() throws Exception {
        String admin = login(adminLoginId);
        LocalDate todayKst = LocalDate.now(KST);

        String wide = mockMvc.perform(get(URL).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // 아주 오래된 하루만 고른다 — 기간을 따라간다면 이 값들이 0 으로 떨어질 것이다.
        String narrow = mockMvc.perform(get(URL
                        + "?from=" + todayKst.minusDays(300).format(DAY)
                        + "&to=" + todayKst.minusDays(300).format(DAY)).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        for (String field : java.util.List.of("today", "thisMonth", "allTime")) {
            org.assertj.core.api.Assertions
                    .assertThat(((Number) JsonPath.read(narrow, "$.data." + field + ".itemSales")).longValue())
                    .as(field + " 이 기간을 따라갔다 — 화면이 「기간과 무관」이라고 적고 있다")
                    .isEqualTo(((Number) JsonPath.read(wide, "$.data." + field + ".itemSales")).longValue());
        }
    }

    @Test
    @DisplayName("잘못된 기간은 거절한다 — 시작>종료(400P) · 366일 초과(400L)")
    void invalidPeriodRejected() throws Exception {
        String admin = login(adminLoginId);
        LocalDate todayKst = LocalDate.now(KST);

        mockMvc.perform(get(URL + "?from=" + todayKst.format(DAY)
                        + "&to=" + todayKst.minusDays(1).format(DAY)).header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STATS-400P"));

        // ⚠ 상한을 두는 이유는 성능이 아니라 **읽을 수 있는가**다 — 빈 날을 채우므로 막대가 그만큼 는다.
        //    🔴 조용히 자르지 않는다(잘린 줄 모르면 「그 기간 매출이 이만큼」으로 잘못 읽는다).
        mockMvc.perform(get(URL + "?from=" + todayKst.minusDays(400).format(DAY)
                        + "&to=" + todayKst.format(DAY)).header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STATS-400L"));

        // 경계 — 정확히 366일은 통과해야 한다(상한을 하나 어긋나게 두기 쉬운 자리).
        mockMvc.perform(get(URL + "?from=" + todayKst.minusDays(365).format(DAY)
                        + "&to=" + todayKst.format(DAY)).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daily.length()").value(366));
    }

    @Test
    @DisplayName("매출은 관리자만 본다 — 미인증 401, 일반 회원 403")
    void requiresAdmin() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).header("Authorization", login(buyerLoginId)))
                .andExpect(status().isForbidden());
    }

    private void assertItemSalesDelta(String admin, long before, long expectedDelta) throws Exception {
        mockMvc.perform(get(URL).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allTime.itemSales").value(before + expectedDelta));
    }

    /**
     * 반품 요청 본문 — <b>남은 것 전부</b>를 담는다 (2026-08-25, G-10).
     *
     * <p>🔴 <b>«비면 전량» 같은 기본값을 안 뒀다</b>(G-10 결정 2) — 화면이 품목을 못 실어 보낸 버그가
     * «전부 반품» 이라는 조용한 동작이 되면 안 되기 때문이다. 그래서 <b>계약이 바뀌었고</b>
     * 옛 호출부가 전부 400 으로 드러났다. 여기서 «전량» 이라고 <b>명시</b>한다.
     *
     * <p>⚠ 수량은 주문 응답의 {@code remainingQuantity} 에서 읽는다 — 손으로 적으면 부분 취소가
     * 섞인 주문에서 어긋난다.
     */
    private String fullReturnBody(String token, String orderId, String reason) throws Exception {
        String order = mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(order, "$.data.items[*].orderItemId");
        List<Integer> remaining = JsonPath.read(order, "$.data.items[*].remainingQuantity");
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (remaining.get(i) <= 0) {
                continue;
            }
            if (!items.isEmpty()) {
                items.append(',');
            }
            items.append("{\"orderItemId\":\"").append(ids.get(i))
                 .append("\",\"quantity\":").append(remaining.get(i)).append('}');
        }
        return "{\"reason\":\"" + reason + "\",\"items\":[" + items + "]}";
    }
}
