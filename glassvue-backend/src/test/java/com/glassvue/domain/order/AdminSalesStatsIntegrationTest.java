package com.glassvue.domain.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
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
    @Autowired EntityManager entityManager;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final String URL = "/api/admin/stats/sales";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private String buyerLoginId;
    private String adminLoginId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        buyerLoginId = "stbuyer_" + suffix;
        adminLoginId = "stadmin_" + suffix;
        member(buyerLoginId, "ZZ통계구매자" + suffix, Role.USER);
        member(adminLoginId, "ZZ통계관리자" + suffix, Role.ADMIN);

        Category cat = categoryRepository.save(Category.builder().name("ZZC-통계" + suffix).build());
        productId = productRepository.save(Product.builder()
                .name("ZZP-통계상품" + suffix).description("d").price(10_000).stock(1000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
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
                .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
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

        // 서비스가 쓰는 것과 **같은 정의**를 SQL 로 다시 쓴다. 둘이 갈라지면 여기서 잡힌다.
        Object[] expected = (Object[]) entityManager.createNativeQuery("""
                SELECT COUNT(*),
                       NVL(SUM(o.total_price - o.coupon_discount), 0),
                       NVL(SUM(o.shipping_fee), 0)
                  FROM orders o
                 WHERE o.status IN ('PAID','SHIPPED','DELIVERED')
                """).getSingleResult();

        mockMvc.perform(get(URL).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allTime.orderCount").value(((Number) expected[0]).longValue()))
                .andExpect(jsonPath("$.data.allTime.itemSales").value(((Number) expected[1]).longValue()))
                .andExpect(jsonPath("$.data.allTime.shippingSales").value(((Number) expected[2]).longValue()));
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
}
