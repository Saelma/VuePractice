package com.glassvue.domain.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기간 할인(타임세일) — 착수부터 목록 정렬까지 (2026-08-19, BACKLOG G-5).
 *
 * <p>여기서 고정하는 계약:
 * <ul>
 *   <li><b>권한</b> — 비로그인 401 · 일반회원 403 · 관리자 200 (WORKING-AGREEMENTS §2-4).</li>
 *   <li>🔴 <b>옵션 가격차와의 곱 순서</b> — 가격차를 <b>더한 뒤에</b> 할인율이 먹는다.
 *       ⚠ 실측(2026-08-19) 운영의 옵션 10개가 <b>전부 {@code priceDelta = 0}</b> 이라,
 *       그 데이터로는 순서를 뒤집어도 결과가 같다. <b>표본을 일부러 만들어서</b> 밟는다
 *       (WA §3-3 — 「0」이라는 답에는 «밟았는데 0» 과 «안 밟아서 0» 둘이 있다).</li>
 *   <li><b>겹침 거절</b> — Oracle 유니크로 못 막아 앱이 유일한 방어다. 맞닿는 경계는 <b>겹침이 아니다.</b></li>
 *   <li><b>정렬·가격필터가 세일가를 본다</b> — 화면에만 있고 조회에는 없는 규칙이 되지 않게.</li>
 * </ul>
 *
 * <p>⚠ <b>공유 espdb 라 운영 데이터가 섞여 있다.</b> 그래서 목록 단언은 «전체 순서» 가 아니라
 * <b>이름으로 좁힌 내 표본 안에서만</b> 한다(2026-08-13 §9-4 가 그 반대로 하다 깨진 자리다).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductDiscountIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 기본가 10,000 — 20% 면 8,000 으로 딱 떨어져 단언이 반올림에 흔들리지 않는다. */
    private static final long BASE_PRICE = 10_000L;
    /** 🔴 **0이 아닌 가격차**. 이 값이 있어야 곱 순서가 증명된다. */
    private static final long DELTA = 2_000L;

    private String suffix;
    private String adminLoginId;
    private String memberLoginId;
    private UUID categoryId;
    private UUID productId;
    private String productName;

    @BeforeEach
    void setUp() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        adminLoginId = "dc_a_" + suffix;
        memberLoginId = "dc_m_" + suffix;
        productName = "ZZP-세일상품" + suffix;

        memberRepository.save(Member.builder().loginId(adminLoginId)
                .password(passwordEncoder.encode(PW)).nickname("ZZ세일관리자" + suffix)
                .role(Role.ADMIN).build());
        memberRepository.save(Member.builder().loginId(memberLoginId)
                .password(passwordEncoder.encode(PW)).nickname("ZZ세일회원" + suffix)
                .role(Role.USER).build());
        categoryId = categoryRepository.save(
                Category.builder().name("ZZC-세일" + suffix).build()).getId();
        productId = createProduct();
    }

    // ── 도우미 ────────────────────────────────────────────────

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /**
     * 옵션 둘짜리 상품 — 「기본」({@code delta 0})과 「L」({@code delta +2000}).
     * 🔴 <b>두 번째 옵션이 이 테스트의 핵심 표본이다.</b>
     */
    private UUID createProduct() throws Exception {
        String body = "{\"name\":\"" + productName + "\",\"description\":\"설명\","
                + "\"price\":" + BASE_PRICE + ",\"status\":\"SELLING\","
                + "\"categoryId\":\"" + categoryId + "\",\"variants\":["
                + "{\"name\":\"기본\",\"priceDelta\":0,\"stock\":5},"
                + "{\"name\":\"L\",\"priceDelta\":" + DELTA + ",\"stock\":5}]}";
        // ⚠ 상품 등록은 **201** 이다(할인 등록은 200). 같은 관리자 조작인데 갈려 있어 헷갈리는 자리다.
        String res = mockMvc.perform(post("/api/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login(adminLoginId)).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(res, "$.data"));
    }

    private String discountBody(int rate, LocalDate start, LocalDate end) {
        return "{\"rate\":" + rate + ",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}";
    }

    private String url() {
        return "/api/admin/products/" + productId + "/discounts";
    }

    private LocalDate today() {
        return LocalDate.now(KST);
    }

    /** 오늘 하루짜리 세일을 걸고 id 를 준다 — 「지금 진행 중」 표본. */
    private String createTodayDiscount(int rate) throws Exception {
        String res = mockMvc.perform(post(url()).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login(adminLoginId))
                        .content(discountBody(rate, today(), today())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data");
    }

    // ── 권한 (WA §2-4) ────────────────────────────────────────

    @Test
    @DisplayName("비로그인은 401 — 세일을 거는 것은 관리자만이다")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(post(url()).contentType(JSON)
                        .content(discountBody(20, today(), today())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("🔴 일반 회원은 403 — 매처를 잊으면 permitAll 로 떨어지는 자리다")
    void memberIsForbidden() throws Exception {
        mockMvc.perform(post(url()).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login(memberLoginId))
                        .content(discountBody(20, today(), today())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자는 200")
    void adminCanCreate() throws Exception {
        mockMvc.perform(post(url()).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login(adminLoginId))
                        .content(discountBody(20, today(), today())))
                .andExpect(status().isOk());
    }

    // ── 값 (곱 순서 · 세 값) ──────────────────────────────────

    @Test
    @DisplayName("🔴 가격차를 **더한 뒤에** 할인율이 먹는다 — 모든 옵션이 같은 비율로 싸진다")
    void discountAppliesAfterPriceDelta() throws Exception {
        createTodayDiscount(20);

        // 기본가 10,000 → 8,000 / L 은 12,000 → 9,600.
        // ⚠ 순서를 뒤집으면(할인 먼저, 가격차 나중) L 은 8,000 + 2,000 = **10,000** 이 된다.
        //    두 기대값이 다르므로 이 단언은 순서를 실제로 가른다.
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(8000))
                .andExpect(jsonPath("$.data.regularPrice").value(10000))
                .andExpect(jsonPath("$.data.discountRate").value(20))
                .andExpect(jsonPath("$.data.variants[0].price").value(8000))
                .andExpect(jsonPath("$.data.variants[0].regularPrice").value(10000))
                .andExpect(jsonPath("$.data.variants[1].price").value(9600))
                .andExpect(jsonPath("$.data.variants[1].regularPrice").value(12000));
    }

    @Test
    @DisplayName("세일이 없으면 price == regularPrice 이고 discountRate 는 null — 예전과 같은 응답이다")
    void withoutDiscountNothingChanges() throws Exception {
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(10000))
                .andExpect(jsonPath("$.data.regularPrice").value(10000))
                .andExpect(jsonPath("$.data.discountRate").doesNotExist())
                .andExpect(jsonPath("$.data.variants[1].price").value(12000));
    }

    @Test
    @DisplayName("⚠ **예정** 세일은 가격을 안 바꾼다 — 미리 걸어 두는 것이 이 기능의 목적이다")
    void upcomingDiscountDoesNotApply() throws Exception {
        LocalDate nextWeek = today().plusDays(7);
        mockMvc.perform(post(url()).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login(adminLoginId))
                        .content(discountBody(30, nextWeek, nextWeek.plusDays(1))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.data.price").value(10000))
                .andExpect(jsonPath("$.data.discountRate").doesNotExist());
    }

    @Test
    @DisplayName("⚠ **지난** 세일도 가격을 안 바꾼다 — 끝난 세일은 기록으로만 남는다")
    void endedDiscountDoesNotApply() throws Exception {
        LocalDate lastWeek = today().minusDays(7);
        mockMvc.perform(post(url()).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login(adminLoginId))
                        .content(discountBody(30, lastWeek, lastWeek.plusDays(1))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.data.price").value(10000))
                .andExpect(jsonPath("$.data.discountRate").doesNotExist());
    }

    // ── 겹침 (앱이 유일한 방어) ───────────────────────────────

    @Test
    @DisplayName("🔴 기간이 겹치면 400 — DB 는 이걸 못 막는다")
    void overlappingIsRejected() throws Exception {
        createTodayDiscount(20);

        mockMvc.perform(post(url()).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login(adminLoginId))
                        .content(discountBody(30, today(), today().plusDays(2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PRODUCT-400DO"));
    }

    @Test
    @DisplayName("🔴 경계가 **맞닿는 것은 겹침이 아니다** — 연속된 세일을 이어 붙일 수 있어야 한다")
    void touchingBoundaryIsAllowed() throws Exception {
        createTodayDiscount(20); // 오늘 하루 (종료 경계 = 내일 00:00)

        // 내일부터 시작 — 종료가 배타라 한 순간도 함께 유효하지 않다.
        mockMvc.perform(post(url()).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login(adminLoginId))
                        .content(discountBody(30, today().plusDays(1), today().plusDays(2))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("종료일이 시작일보다 앞이면 400 — 조용히 아무 일도 안 하는 행을 만들지 않는다")
    void reversedPeriodIsRejected() throws Exception {
        mockMvc.perform(post(url()).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login(adminLoginId))
                        .content(discountBody(20, today(), today().minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PRODUCT-400DP"));
    }

    @Test
    @DisplayName("할인율 0·100 은 400 — 0은 할인이 아니고 100은 결제가 통째로 0원 경로가 된다")
    void rateOutOfRangeIsRejected() throws Exception {
        for (int rate : new int[]{0, 100}) {
            mockMvc.perform(post(url()).contentType(JSON)
                            .header(HttpHeaders.AUTHORIZATION, login(adminLoginId))
                            .content(discountBody(rate, today(), today())))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── 수정 · 삭제 ───────────────────────────────────────────

    @Test
    @DisplayName("🔴 기간을 그대로 두고 할인율만 고칠 수 있다 — 겹침 검사가 **자기 자신을 뺀다**")
    void updateKeepingSamePeriod() throws Exception {
        String discountId = createTodayDiscount(20);

        mockMvc.perform(put(url() + "/" + discountId).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login(adminLoginId))
                        .content(discountBody(50, today(), today())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.data.price").value(5000))
                .andExpect(jsonPath("$.data.discountRate").value(50));
    }

    @Test
    @DisplayName("지우면 그 순간 원가로 돌아온다 — 잘못 건 세일을 되돌리는 유일한 방법이다")
    void deleteRestoresPrice() throws Exception {
        String discountId = createTodayDiscount(20);

        mockMvc.perform(delete(url() + "/" + discountId)
                        .header(HttpHeaders.AUTHORIZATION, login(adminLoginId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.data.price").value(10000))
                .andExpect(jsonPath("$.data.discountRate").doesNotExist());
    }

    @Test
    @DisplayName("⚠ 다른 상품 경로로는 못 지운다 — 안 건드린 상품의 세일이 사라지면 나중에야 안다")
    void cannotTouchAnotherProductsDiscount() throws Exception {
        String discountId = createTodayDiscount(20);
        UUID otherProductId = createProduct();

        mockMvc.perform(delete("/api/admin/products/" + otherProductId + "/discounts/" + discountId)
                        .header(HttpHeaders.AUTHORIZATION, login(adminLoginId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT-404D"));
    }

    // ── 목록: 정렬·가격필터가 세일가를 본다 ────────────────────

    @Test
    @DisplayName("🔴 가격 필터가 **세일가**를 본다 — 화면엔 8,000인데 「1만원 이하」에 안 걸리면 안 된다")
    void priceFilterUsesSalePrice() throws Exception {
        // 🔴 **대조군을 「세일 없는 다른 상품」으로 둔다** — 둘 다 기본가 10,000 이고 세일만 다르다.
        //    ⚠ 예전에는 «같은 조회를 세일 전후로 두 번» 불러 비교했는데 그 방식이 **간헐적으로 실패했다**
        //       (2026-08-19 실측: 3~4회 중 1~2회). 두 호출 사이에 `products:list` 캐시 쓰기와
        //       `@CacheEvict` 가 끼어 있는 구조였고, **원인은 확정하지 못했다**
        //       (EntityManager flush 가설은 세워서 밟아 봤고 **기각됐다**).
        //    → 원인을 모르는 채로 «가끔 빨개지는 테스트» 를 남기지 않는다. 한 번의 조회로 둘을 함께
        //       증명하면 그 의존이 아예 없어지고, **대조군의 값어치는 오히려 커진다**
        //       (같은 순간·같은 쿼리에서 «걸리는 것» 과 «안 걸리는 것» 을 나란히 본다, WA §3-3).
        String plainName = productName + "-대조군";
        String body = "{\"name\":\"" + plainName + "\",\"description\":\"설명\","
                + "\"price\":" + BASE_PRICE + ",\"status\":\"SELLING\",\"categoryId\":\"" + categoryId + "\","
                + "\"variants\":[{\"name\":\"기본\",\"priceDelta\":0,\"stock\":5}]}";
        mockMvc.perform(post("/api/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login(adminLoginId)).content(body))
                .andExpect(status().isCreated());

        createTodayDiscount(20); // 세일 상품만 8,000 이 된다

        // 「9,000 이하」로 걸러 보면 **세일 상품만** 나온다. 대조군은 10,000 이라 안 걸린다 —
        // 즉 이 단언은 «필터가 세일가를 본다» 와 «필터가 실제로 거르고 있다» 를 동시에 말한다.
        mockMvc.perform(get("/api/products").param("name", productName).param("maxPrice", "9000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value(productName))
                .andExpect(jsonPath("$.data.content[0].price").value(8000));
    }

    @Test
    @DisplayName("🔴 가격 정렬이 **세일가** 순이다 — 목록에 뜬 값과 순서가 같은 것을 본다")
    void priceSortUsesSalePrice() throws Exception {
        // 같은 이름 접두사를 가진 상품 둘: A(기본가 10,000, 세일 20% → 8,000) · B(기본가 9,000, 세일 없음)
        createTodayDiscount(20);
        String cheaperName = productName + "-B";
        String body = "{\"name\":\"" + cheaperName + "\",\"description\":\"설명\","
                + "\"price\":9000,\"status\":\"SELLING\",\"categoryId\":\"" + categoryId + "\","
                + "\"variants\":[{\"name\":\"기본\",\"priceDelta\":0,\"stock\":5}]}";
        mockMvc.perform(post("/api/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login(adminLoginId)).content(body))
                .andExpect(status().isCreated());

        // 세일 전이라면 9,000(B) < 10,000(A) 라 B 가 먼저다.
        // 세일이 붙으면 8,000(A) < 9,000(B) 로 **순서가 뒤집힌다** — 그게 이 단언의 값어치다.
        mockMvc.perform(get("/api/products")
                        .param("name", productName).param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].name").value(productName))
                .andExpect(jsonPath("$.data.content[0].price").value(8000))
                .andExpect(jsonPath("$.data.content[1].name").value(cheaperName))
                .andExpect(jsonPath("$.data.content[1].price").value(9000));
    }

    // ── 목록 조회(관리자) ─────────────────────────────────────

    @Test
    @DisplayName("할인 목록은 지난 것·진행 중·예정을 시간순으로 모두 준다 — status 는 서버가 정한다")
    void listReturnsAllWithStatus() throws Exception {
        LocalDate lastWeek = today().minusDays(7);
        LocalDate nextWeek = today().plusDays(7);
        String auth = login(adminLoginId);
        mockMvc.perform(post(url()).contentType(JSON).header(HttpHeaders.AUTHORIZATION, auth)
                .content(discountBody(10, lastWeek, lastWeek))).andExpect(status().isOk());
        createTodayDiscount(20);
        mockMvc.perform(post(url()).contentType(JSON).header(HttpHeaders.AUTHORIZATION, auth)
                .content(discountBody(30, nextWeek, nextWeek))).andExpect(status().isOk());

        mockMvc.perform(get(url()).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].status").value("ENDED"))
                .andExpect(jsonPath("$.data[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[2].status").value("UPCOMING"))
                // 🔴 **종료일이 되돌아온다** — 배타 경계에서 하루를 빼지 않으면 여기가 깨진다.
                //    안 빼면 폼을 다시 열 때마다 세일이 하루씩 길어진다.
                .andExpect(jsonPath("$.data[1].startDate").value(today().toString()))
                .andExpect(jsonPath("$.data[1].endDate").value(today().toString()));
    }
}
