package com.glassvue.domain.coupon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
 * 프로모션 달력에 <b>상품 타임세일</b>이 실린다 (2026-08-19, B-27 + G-5).
 *
 * <p>🔴 <b>이 화면이 원래 답하려던 질문에 오늘부터 답한다</b> — 그전까지 겹칠 것이 쿠폰뿐이라
 * «겹침» 이 사실상 «발급 창끼리» 하나였다. 상품 세일이 얹히면서 <b>쿠폰 발급 창과 세일이 같은 날에
 * 있는 것</b>이 보이게 됐고, 그 날은 이미 깎인 세일가 위에 쿠폰이 또 붙는다.
 *
 * <p>여기서 고정하는 계약:
 * <ul>
 *   <li>세일이 <b>격자 막대</b>로 온다({@code kind=SALE}, {@code gridded=true}).</li>
 *   <li>🔴 <b>종료일이 하루 밀리지 않는다</b> — {@code endsAt} 은 배타 경계라 하루를 빼야 한다.
 *       안 빼면 달력에서 세일이 <b>하루 더 길어 보인다</b>(관리자 폼과 같은 자리의 같은 함정).</li>
 *   <li>⚠ <b>삭제 대기 상품의 세일은 안 온다</b>(F-7) — 목록에 안 나오는 상품이다.</li>
 *   <li>달 밖으로 삐져나간 막대는 <b>잘리고 그 사실이 플래그로</b> 온다.</li>
 * </ul>
 *
 * <p>⚠ <b>공유 espdb 라 운영 쿠폰·상품이 섞여 있다.</b> 그래서 «막대가 N개다» 를 세지 않고
 * <b>내가 만든 상품의 막대만 골라</b> 단언한다(2026-08-13 §9-4 가 그 반대로 하다 깨진 자리).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PromotionCalendarSaleIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String adminLoginId;
    private UUID categoryId;
    private UUID productId;
    private String productName;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminLoginId = "cal_" + suffix;
        productName = "ZZP-달력세일" + suffix;
        memberRepository.save(Member.builder().loginId(adminLoginId)
                .password(passwordEncoder.encode(PW)).nickname("ZZ달력관리자" + suffix)
                .role(Role.ADMIN).build());
        categoryId = categoryRepository.save(
                Category.builder().name("ZZC-달력" + suffix).build()).getId();
        productId = createProduct(productName);
    }

    // ── 도우미 ────────────────────────────────────────────────

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + adminLoginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private UUID createProduct(String name) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"description\":\"설명\",\"price\":10000,"
                + "\"status\":\"SELLING\",\"categoryId\":\"" + categoryId + "\","
                + "\"variants\":[{\"name\":\"기본\",\"priceDelta\":0,\"stock\":5}]}";
        String res = mockMvc.perform(post("/api/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login()).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(res, "$.data"));
    }

    private void createDiscount(UUID pid, int rate, LocalDate start, LocalDate end) throws Exception {
        mockMvc.perform(post("/api/admin/products/" + pid + "/discounts").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, login())
                        .content("{\"rate\":" + rate + ",\"startDate\":\"" + start
                                + "\",\"endDate\":\"" + end + "\"}"))
                .andExpect(status().isOk());
    }

    /**
     * 그 달의 막대 중 <b>내 상품 것만</b> 골라 준다.
     *
     * <p>🔴 <b>JsonPath 필터에 {@code .length()} 를 붙이지 않는다</b>(2026-08-19 실측) —
     * {@code $.data.spans[?(@.name == '...')].length()} 가 <b>걸러진 개수가 아니라 전체 개수(10)</b>를
     * 돌려줬고, 일치하는 게 없으면 «No matching value» 로 터졌다. 즉 «0건이다» 를 단언할 수가 없다.
     * ⚠ <b>공유 espdb 라 운영 막대가 늘 섞여 있어</b> 필터가 이 테스트의 전제인데, 그 필터가
     * 미덥지 않으면 초록이 아무것도 증명하지 못한다. → <b>Java 에서 거른다.</b>
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mySpans(YearMonth month) throws Exception {
        String body = calendar(month).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> all = JsonPath.read(body, "$.data.spans");
        return all.stream().filter(sp -> productName.equals(sp.get("name"))).toList();
    }

    private org.springframework.test.web.servlet.ResultActions calendar(YearMonth month)
            throws Exception {
        return mockMvc.perform(get("/api/admin/coupons/calendar")
                        .param("month", month.toString())
                        .header(HttpHeaders.AUTHORIZATION, login()))
                .andExpect(status().isOk());
    }

    /** 이 달 안에서 넉넉히 떨어진 날짜 — 달 경계에 걸려 테스트가 흔들리지 않게. */
    private LocalDate dayInThisMonth(int day) {
        return YearMonth.now(KST).atDay(day);
    }

    // ── 세일이 격자에 온다 ────────────────────────────────────

    @Test
    @DisplayName("🔴 세일이 **격자 막대**로 온다 — kind=SALE · gridded=true")
    void saleAppearsAsGriddedBar() throws Exception {
        createDiscount(productId, 20, dayInThisMonth(10), dayInThisMonth(12));

        List<Map<String, Object>> mine = mySpans(YearMonth.now(KST));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0)).containsEntry("kind", "SALE")
                .containsEntry("gridded", true)
                .containsEntry("label", "20% 할인");
    }

    @Test
    @DisplayName("🔴 **종료일이 하루 밀리지 않는다** — endsAt 은 배타 경계다")
    void endDayIsNotShiftedByOne() throws Exception {
        createDiscount(productId, 20, dayInThisMonth(10), dayInThisMonth(12));

        // 관리자가 「12일까지」라고 적었으면 달력도 12일에서 끝나야 한다.
        // ⚠ 하루를 안 빼면 여기가 13 이 된다 — 화면은 멀쩡하고 세일만 하루 길어 보인다.
        List<Map<String, Object>> mine = mySpans(YearMonth.now(KST));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0)).containsEntry("startDay", 10).containsEntry("endDay", 12);
    }

    @Test
    @DisplayName("하루짜리 세일은 시작일 == 종료일이다 — 막대가 사라지지 않는다")
    void oneDaySale() throws Exception {
        createDiscount(productId, 30, dayInThisMonth(15), dayInThisMonth(15));

        List<Map<String, Object>> mine = mySpans(YearMonth.now(KST));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0)).containsEntry("startDay", 15).containsEntry("endDay", 15);
    }

    @Test
    @DisplayName("⚠ 다른 달의 세일은 안 온다 — 달력은 그 달만 답한다")
    void otherMonthSaleIsNotIncluded() throws Exception {
        createDiscount(productId, 20, dayInThisMonth(10), dayInThisMonth(12));

        // 두 달 뒤를 물으면 내 막대가 없어야 한다.
        assertThat(mySpans(YearMonth.now(KST).plusMonths(2))).isEmpty();
    }

    // ── 삭제 대기 상품 (F-7) ──────────────────────────────────

    @Test
    @DisplayName("🔴 **삭제 대기 상품의 세일은 달력에 안 뜬다** — 목록에 안 나오는 상품이다")
    void deletedProductSaleIsHidden() throws Exception {
        createDiscount(productId, 20, dayInThisMonth(10), dayInThisMonth(12));
        // 🔴 대조군: 지우기 **전에는 보인다** — 이게 없으면 «지워서 안 보인다» 와
        //    «원래 없었다» 가 구분되지 않는다(WA §3-3).
        assertThat(mySpans(YearMonth.now(KST))).hasSize(1);

        // ⚠ 상품 삭제는 200 이다(204 가 아니다). 등록이 201 인 것과 함께 헷갈리는 자리.
        mockMvc.perform(delete("/api/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, login()))
                .andExpect(status().isOk());

        assertThat(mySpans(YearMonth.now(KST))).isEmpty();
    }

    // ── 쿠폰 막대는 그대로다 ──────────────────────────────────

    @Test
    @DisplayName("⚠ 쿠폰 막대의 계약이 안 깨졌다 — label 이 채워져 오고 gridded 가 있다")
    void couponBarsStillWork() throws Exception {
        // 운영에 쿠폰이 늘 있으므로 «막대가 하나 이상 있고, 그 전부가 label 을 갖는다» 를 본다.
        // ⚠ 특정 쿠폰 이름을 단언하지 않는다 — 운영 데이터에 기대면 언젠가 깨진다.
        calendar(YearMonth.now(KST))
                .andExpect(jsonPath("$.data.spans[*].label")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString()))))
                .andExpect(jsonPath("$.data.spans[?(@.kind == 'USE')].gridded")
                        .exists());
    }
}
