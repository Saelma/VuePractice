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
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.util.List;
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
 * 관리자 대시보드의 「재고 부족」 (2026-08-03, 백로그 B-16).
 *
 * <p>여기서만 드러나는 것 넷:
 * <ol>
 *   <li><b>{@code <=} 인가 {@code <} 인가</b> — 기준값과 <i>정확히 같은</i> 재고가 부족에 들어가야 한다.
 *       경계 하나 차이라 눈으로는 안 보이고, 틀려도 화면은 멀쩡히 뜬다.</li>
 *   <li><b>{@code HIDDEN} 제외</b> — 숨긴 상품이 섞이면 아무도 손댈 필요 없는 줄이 "할 일" 목록에 낀다.</li>
 *   <li><b>{@code count} 와 {@code items} 가 같은 조건을 쓰는가</b> — 둘이 갈리면
 *       "3건"이라 써 놓고 목록엔 5줄이 뜬다. 쿼리가 둘이라 실제로 갈릴 수 있는 자리다.</li>
 *   <li><b>권한</b> — 재고는 운영 정보다. 401·403 을 계약으로 고정한다(WA §2-4).</li>
 * </ol>
 *
 * <p>⚠ <b>공유 espdb 에 상품·재고가 이미 쌓여 있다.</b> 그래서 절대 건수를 단정하지 않고
 * <b>증분</b>으로 본다(매출 통계 테스트와 같은 판단). 같은 이유로 <b>"내가 만든 옵션이 목록에 보인다"도
 * 단정하지 않는다</b> — 목록은 재고 적은 순 상위 몇 줄이라, 기존 데이터가 그 자리를 채우고 있으면
 * 정상 동작인데도 안 보일 수 있다. 대신 목록은 <b>불변식</b>(정렬·상한·조건)과 <b>부재</b>로 고정한다
 * — 부재는 기존 데이터가 늘어나도 뒤집히지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminLowStockIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired EntityManager entityManager;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final String URL = "/api/admin/products/low-stock";

    /** application.yml 의 {@code catalog.low-stock-threshold}. 값이 바뀌면 이 테스트가 먼저 알려준다. */
    private static final long THRESHOLD = 5;

    /** {@code ProductQueryService.LOW_STOCK_ITEMS}. 목록 상한. */
    private static final int ITEM_LIMIT = 8;

    private String suffix;
    private String userLoginId;
    private String adminLoginId;
    private Category category;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        userLoginId = "lsuser_" + suffix;
        adminLoginId = "lsadmin_" + suffix;
        member(userLoginId, "ZZ재고일반" + suffix, Role.USER);
        member(adminLoginId, "ZZ재고관리자" + suffix, Role.ADMIN);
        category = categoryRepository.save(Category.builder().name("ZZC-재고" + suffix).build());
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

    /** 상품 하나 + 옵션 하나. 옵션 재고와 상품 상태를 지정해 조건을 하나씩 재현한다. */
    private UUID productWithVariant(String label, ProductStatus status, long stock) {
        UUID productId = productRepository.save(Product.builder()
                .name("ZZP-재고" + label + suffix).description("d").price(10_000)
                .status(status).category(category).build()).getId();
        variantRepository.save(ProductVariant.of(productId, "기본", 0, stock, 0));
        // 벌크가 아닌 save 라 flush 만으로 충분하지만, 조회가 같은 트랜잭션에서 이뤄지므로 명시한다.
        entityManager.flush();
        return productId;
    }

    private String body(String admin) throws Exception {
        return mockMvc.perform(get(URL).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long count(String admin) throws Exception {
        return ((Number) JsonPath.read(body(admin), "$.data.count")).longValue();
    }

    @Test
    @DisplayName("기준값을 응답에 실어 준다 — 화면이 '몇 개 이하'를 스스로 적지 않게")
    void exposesThreshold() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", login(adminLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.threshold").value(THRESHOLD));
    }

    @Test
    @DisplayName("⚠ 경계 — 재고가 기준값과 **같으면** 부족이다 (`<=`, `<` 가 아니다)")
    void thresholdIsInclusive() throws Exception {
        String admin = login(adminLoginId);

        long before = count(admin);
        productWithVariant("경계", ProductStatus.SELLING, THRESHOLD);
        assertThat(count(admin))
                .as("재고 %d 은 기준값 %d 이하이므로 부족에 들어가야 한다", THRESHOLD, THRESHOLD)
                .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("⚠ 경계 — 기준값보다 하나 많으면 부족이 아니다")
    void aboveThresholdIsExcluded() throws Exception {
        String admin = login(adminLoginId);

        long before = count(admin);
        productWithVariant("여유", ProductStatus.SELLING, THRESHOLD + 1);
        assertThat(count(admin))
                .as("재고 %d 은 기준값 %d 을 넘으므로 부족이 아니다", THRESHOLD + 1, THRESHOLD)
                .isEqualTo(before);
    }

    @Test
    @DisplayName("품절(재고 0)도 부족에 들어간다 — 가장 급한 줄이 빠지면 안 된다")
    void zeroStockIsIncluded() throws Exception {
        String admin = login(adminLoginId);

        long before = count(admin);
        productWithVariant("영", ProductStatus.SELLING, 0);
        assertThat(count(admin)).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("⚠ 숨김(HIDDEN) 상품은 세지 않는다 — 팔지 않는 상품은 채울 이유가 없다")
    void hiddenProductIsExcluded() throws Exception {
        String admin = login(adminLoginId);

        long before = count(admin);
        productWithVariant("숨김", ProductStatus.HIDDEN, 0);
        assertThat(count(admin))
                .as("숨긴 상품의 재고 0 옵션은 '처리해야 할 것'이 아니다")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("⚠ 숨김 상품은 **목록에도** 안 나온다 — count 만 거르고 items 가 새면 숫자와 줄이 어긋난다")
    void hiddenProductIsAbsentFromItems() throws Exception {
        String admin = login(adminLoginId);
        productWithVariant("숨김목록", ProductStatus.HIDDEN, 0);

        // 부재 단정이라 기존 데이터가 얼마나 쌓여 있든 뒤집히지 않는다.
        List<Object> hit = JsonPath.read(body(admin),
                "$.data.items[?(@.productName == 'ZZP-재고숨김목록" + suffix + "')]");
        assertThat(hit).as("숨긴 상품이 재고 부족 목록에 보이면 안 된다").isEmpty();
    }

    @Test
    @DisplayName("품절 표시(SOLD_OUT) 상품은 **포함**한다 — 재입고가 필요한 건 그대로다")
    void soldOutProductIsIncluded() throws Exception {
        String admin = login(adminLoginId);

        long before = count(admin);
        productWithVariant("품절표시", ProductStatus.SOLD_OUT, 0);
        assertThat(count(admin)).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("목록은 재고 적은 순이고, 상한(8줄)을 넘지 않으며, 모든 줄이 기준값 이하다")
    void itemsAreSortedCappedAndWithinThreshold() throws Exception {
        String admin = login(adminLoginId);
        // 정렬을 실제로 흔들기 위해 재고가 서로 다른 옵션을 넣는다.
        productWithVariant("정렬3", ProductStatus.SELLING, 3);
        productWithVariant("정렬0", ProductStatus.SELLING, 0);
        productWithVariant("정렬5", ProductStatus.SELLING, THRESHOLD);

        String body = body(admin);
        List<Integer> stocks = JsonPath.read(body, "$.data.items[*].stock");
        long total = ((Number) JsonPath.read(body, "$.data.count")).longValue();

        assertThat(stocks).as("목록은 카드에 들어가는 만큼만").hasSizeLessThanOrEqualTo(ITEM_LIMIT);
        assertThat(stocks.size()).as("목록이 전체 건수보다 많을 수는 없다").isLessThanOrEqualTo((int) total);
        assertThat(stocks).as("모든 줄이 기준값 이하여야 한다")
                .allSatisfy(s -> assertThat((long) s).isLessThanOrEqualTo(THRESHOLD));
        assertThat(stocks).as("재고 적은 순 — 급한 것이 위로").isSorted();
    }

    @Test
    @DisplayName("목록의 각 줄은 상품 ID 를 들고 있다 — 대시보드에서 고치러 갈 길")
    void itemsCarryProductId() throws Exception {
        String admin = login(adminLoginId);
        productWithVariant("링크", ProductStatus.SELLING, 0);

        List<String> ids = JsonPath.read(body(admin), "$.data.items[*].productId");
        assertThat(ids).isNotEmpty().allSatisfy(id -> assertThat(id).isNotBlank());
    }

    @Test
    @DisplayName("count 가 DB 직접 집계와 일치한다 — 조건이 코드와 SQL 양쪽에서 같은지")
    void countMatchesDirectAggregate() throws Exception {
        String admin = login(adminLoginId);
        productWithVariant("대조판매", ProductStatus.SELLING, 1);
        productWithVariant("대조숨김", ProductStatus.HIDDEN, 1);

        // 서비스가 쓰는 것과 **같은 정의**를 SQL 로 다시 쓴다. 둘이 갈라지면 여기서 잡힌다.
        Number expected = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                  FROM product_variant v
                  JOIN product p ON p.id = v.product_id
                 WHERE p.status <> 'HIDDEN'
                   AND v.stock <= ?1
                """).setParameter(1, THRESHOLD).getSingleResult();

        assertThat(count(admin)).isEqualTo(expected.longValue());
    }

    @Test
    @DisplayName("재고 부족은 관리자만 본다 — 미인증 401, 일반 회원 403")
    void requiresAdmin() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());
    }
}
