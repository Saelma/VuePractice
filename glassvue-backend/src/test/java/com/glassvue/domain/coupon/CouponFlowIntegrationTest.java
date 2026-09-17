package com.glassvue.domain.coupon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 생성 → 발급 → 내 목록 조회 관통 + 권한.
 *
 * <p>권한 규칙은 서비스 단위 테스트로 절대 안 잡히고 실제 요청을 보내야만 드러난다(§2-4).
 * 특히 {@code /api/coupons/**} 는 SecurityConfig 의 기본이 {@code permitAll} 이라
 * 매처를 빠뜨리면 <b>남의 쿠폰까지 조용히 열린다</b> — 그래서 401 을 계약으로 고정한다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CouponFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String JSON = "application/json";
    private static final String PW = "password123";

    private String userLoginId;
    private String adminLoginId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userLoginId = "cuser_" + UUID.randomUUID().toString().substring(0, 8);
        adminLoginId = "cadmin_" + UUID.randomUUID().toString().substring(0, 8);
        userId = member(userLoginId, "ZZ쿠폰유저", Role.USER);
        member(adminLoginId, "ZZ쿠폰관리자", Role.ADMIN);
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

    @Test
    @DisplayName("쿠폰 생성(관리자) → 발급 → 내 목록에 보이고 할인 미리보기가 계산된다")
    void createIssueAndList() throws Exception {
        String admin = login(adminLoginId);
        String user = login(userLoginId);

        String body = mockMvc.perform(post("/api/admin/coupons").header("Authorization", admin)
                        .contentType(JSON)
                        .content("{\"name\":\"ZZ 5천원\",\"discountType\":\"FIXED\",\"discountValue\":5000,"
                               + "\"minOrderAmount\":30000,"
                               + "\"validFrom\":\"2026-01-01T00:00:00Z\",\"validUntil\":\"2027-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String couponId = JsonPath.read(body, "$.data");

        mockMvc.perform(post("/api/admin/coupons/" + couponId + "/issue?memberId=" + userId)
                        .header("Authorization", admin))
                .andExpect(status().isOk());

        // 최소 주문금액(30,000)을 채운 경우 — 사용 가능 + 할인액 미리보기
        mockMvc.perform(get("/api/coupons/me?itemsTotal=30000").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("ZZ 5천원"))
                .andExpect(jsonPath("$.data[0].usable").value(true))
                .andExpect(jsonPath("$.data[0].discountPreview").value(5000));

        // 못 채운 경우 — 쓸 수 없고 이유가 함께 온다(화면이 규칙을 몰라도 되게)
        mockMvc.perform(get("/api/coupons/me?itemsTotal=29999").header("Authorization", user))
                .andExpect(jsonPath("$.data[0].usable").value(false))
                .andExpect(jsonPath("$.data[0].discountPreview").value(0))
                .andExpect(jsonPath("$.data[0].reason").isNotEmpty());
    }

    @Test
    @DisplayName("🔴 Q-6 — 정액이 상품합계보다 크면 사라지는 몫(forfeitPreview)을 함께 준다")
    void forfeitPreview_whenFixedExceedsTotal() throws Exception {
        String admin = login(adminLoginId);
        String user = login(userLoginId);

        // Q-4 의 모양 그대로 — 5,000원 쿠폰 / 최소주문 1,000원 (일부러 안 막은 조합)
        String body = mockMvc.perform(post("/api/admin/coupons").header("Authorization", admin)
                        .contentType(JSON)
                        .content("{\"name\":\"ZZ 잘리는쿠폰\",\"discountType\":\"FIXED\",\"discountValue\":5000,"
                               + "\"minOrderAmount\":1000,"
                               + "\"validFrom\":\"2026-01-01T00:00:00Z\",\"validUntil\":\"2027-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String couponId = JsonPath.read(body, "$.data");
        mockMvc.perform(post("/api/admin/coupons/" + couponId + "/issue?memberId=" + userId)
                        .header("Authorization", admin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/coupons/me?itemsTotal=1000").header("Authorization", user))
                .andExpect(jsonPath("$.data[0].discountPreview").value(1000))
                .andExpect(jsonPath("$.data[0].forfeitPreview").value(4000));

        // 대조군 — 안 잘리면 0
        mockMvc.perform(get("/api/coupons/me?itemsTotal=5000").header("Authorization", user))
                .andExpect(jsonPath("$.data[0].discountPreview").value(5000))
                .andExpect(jsonPath("$.data[0].forfeitPreview").value(0));

        // 못 쓰는 쿠폰은 사라질 것도 없다 — 최소주문 미달
        mockMvc.perform(get("/api/coupons/me?itemsTotal=999").header("Authorization", user))
                .andExpect(jsonPath("$.data[0].usable").value(false))
                .andExpect(jsonPath("$.data[0].forfeitPreview").value(0));
    }

    @Test
    @DisplayName("권한 — 내 쿠폰은 비로그인 401 / 쿠폰 생성·발급은 일반 사용자 403")
    void permissions() throws Exception {
        String user = login(userLoginId);

        // ⚠ SecurityConfig 기본이 permitAll이라 매처가 빠지면 여기서 200이 난다.
        mockMvc.perform(get("/api/coupons/me")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/coupons").header("Authorization", user)
                        .contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/coupons/" + UUID.randomUUID() + "/issue?memberId=" + userId)
                        .header("Authorization", user))
                .andExpect(status().isForbidden());
    }

    /**
     * 쿠폰 정의 목록(관리자 쿠폰 관리 화면) — 401/403/200.
     *
     * <p>⚠ 생성·발급(위 {@code permissions})만 덮고 <b>목록(GET)이 빠져 있었다</b>(2026-07-28 → 07-29 이월).
     * {@code /api/admin/**} 블랭킷 규칙으로 보호되기는 하나 그게 테스트 면제 사유는 아니다(§2-4) —
     * 규칙이 나중에 좁혀지거나 이 경로가 밖으로 나가면 <b>쿠폰 정의가 통째로 열린다</b>.
     */
    @Test
    @DisplayName("쿠폰 목록(관리자) — 비로그인 401 / USER 403 / ADMIN 200 + 방금 만든 쿠폰이 최신순 첫 줄")
    void adminList_permissionAndContent() throws Exception {
        mockMvc.perform(get("/api/admin/coupons")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/coupons").header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());

        String admin = login(adminLoginId);
        String name = "ZZ목록확인 " + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/admin/coupons").header("Authorization", admin)
                        .contentType(JSON)
                        .content("{\"name\":\"" + name + "\",\"discountType\":\"PERCENT\",\"discountValue\":10,"
                               + "\"minOrderAmount\":10000,\"maxDiscountAmount\":3000,"
                               + "\"validFrom\":\"2026-01-01T00:00:00Z\",\"validUntil\":\"2027-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk());

        // 200 을 상태코드로만 보지 않고 "정의가 실제로 실려 오는지" 까지 본다(기본 정렬 createdAt DESC).
        mockMvc.perform(get("/api/admin/coupons").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value(name))
                .andExpect(jsonPath("$.data.content[0].discountType").value("PERCENT"))
                .andExpect(jsonPath("$.data.content[0].discountValue").value(10))
                .andExpect(jsonPath("$.data.content[0].maxDiscountAmount").value(3000));
    }

    /**
     * 🔴 2026-09-17 브라우저 검증에서 08-28 에 끝난 쿠폰이 수동 발급됐다. ⚠ 대조군 — 기한이 남은 이벤트 쿠폰은
     * <b>발급 창이 닫혀도</b> 관리자가 줄 수 있다(CS 통로, 사용자 결정).
     */
    @Test
    @DisplayName("🔴 사용 기간이 끝난 쿠폰은 수동 발급이 거절되고, 발급 창만 닫힌 이벤트 쿠폰은 발급된다")
    void issueRejectsExpiredButAllowsClosedEventWindow() throws Exception {
        String admin = login(adminLoginId);
        String expired = JsonPath.read(createCoupon(admin, couponBody("FIXED", 1000,
                "2026-01-01T00:00:00Z", "2026-01-31T00:00:00Z")).andReturn().getResponse().getContentAsString(), "$.data");
        String closedEvent = JsonPath.read(createCoupon(admin,
                "{\"name\":\"ZZ 창닫힌이벤트\",\"discountType\":\"FIXED\",\"discountValue\":1000,\"minOrderAmount\":0,"
                        + "\"validFrom\":\"2026-01-01T00:00:00Z\",\"issueUntil\":\"2026-01-01T23:59:59Z\","
                        + "\"validUntil\":\"2099-01-01T00:00:00Z\"}")
                .andReturn().getResponse().getContentAsString(), "$.data");

        mockMvc.perform(post("/api/admin/coupons/" + expired + "/issue?memberId=" + userId).header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COUPON-400F"));
        mockMvc.perform(post("/api/admin/coupons/" + closedEvent + "/issue?memberId=" + userId).header("Authorization", admin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("목록 탭 — ACTIVE 는 사용 마감 전만, EXPIRED 는 지난 것만, 비우면 전부 · 셋 다 최신 생성순")
    void adminListFiltersByStatus() throws Exception {
        String admin = login(adminLoginId);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String expiredName = "ZZ탭만료 " + suffix;
        String activeName = "ZZ탭유효 " + suffix;
        createCoupon(admin, couponBody("FIXED", 1000, "2026-01-01T00:00:00Z", "2026-01-31T00:00:00Z")
                .replace("ZZ Q축", expiredName)).andExpect(status().isOk());
        createCoupon(admin, couponBody("FIXED", 1000, "2026-01-01T00:00:00Z", "2099-01-01T00:00:00Z")
                .replace("ZZ Q축", activeName)).andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/coupons").param("status", "ACTIVE").param("size", "500").header("Authorization", admin))
                .andExpect(jsonPath("$.data.content[0].name").value(activeName))
                .andExpect(jsonPath("$.data.content[?(@.name=='" + expiredName + "')]").isEmpty())
                .andExpect(jsonPath("$.data.content[?(@.expired==true)]").isEmpty());
        mockMvc.perform(get("/api/admin/coupons").param("status", "EXPIRED").param("size", "500").header("Authorization", admin))
                .andExpect(jsonPath("$.data.content[0].name").value(expiredName))
                .andExpect(jsonPath("$.data.content[?(@.name=='" + activeName + "')]").isEmpty())
                .andExpect(jsonPath("$.data.content[?(@.expired==false)]").isEmpty());
        mockMvc.perform(get("/api/admin/coupons").param("size", "500").header("Authorization", admin))
                .andExpect(jsonPath("$.data.content[0].name").value(activeName))
                .andExpect(jsonPath("$.data.content[1].name").value(expiredName));
    }

    // ---------- 값끼리의 관계 (Q 축, 2026-09-10) ----------

    /** 쿠폰 생성 본문을 만든다. 기본은 «정상» 이고, 시험마다 한 칸만 비튼다. */
    private String couponBody(String type, long value, String from, String until) {
        return ("{\"name\":\"ZZ Q축\",\"discountType\":\"%s\",\"discountValue\":%d,"
                + "\"minOrderAmount\":30000,"
                + "\"validFrom\":\"%s\",\"validUntil\":\"%s\"}").formatted(type, value, from, until);
    }

    private ResultActions createCoupon(String admin, String body) throws Exception {
        return mockMvc.perform(post("/api/admin/coupons").header("Authorization", admin)
                .contentType(JSON).content(body));
    }

    @Test
    @DisplayName("🔴 상시 쿠폰도 사용 기간이 뒤집히면 거절된다 — 그전엔 이벤트 쿠폰만 검사했다")
    void plainCouponWithReversedPeriodIsRejected() throws Exception {
        String admin = login(adminLoginId);

        // 사용 마감이 시작보다 앞이다 → 만들어지면 «영원히 못 쓰는 쿠폰» 이 조용히 남는다.
        createCoupon(admin, couponBody("FIXED", 5000, "2027-01-01T00:00:00Z", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());

        // ⚠ 같은 날은? «이후» 를 요구하므로 거절이다 — 0초짜리 쿠폰은 못 쓰는 쿠폰과 같다.
        createCoupon(admin, couponBody("FIXED", 5000, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());

        // 🔴 대조군 — 정상 기간은 만들어진다. 없으면 «전부 거절» 과 구별이 안 된다.
        createCoupon(admin, couponBody("FIXED", 5000, "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("정률 할인은 100을 넘을 수 없다 — 100 자체는 통과한다(경계)")
    void percentOver100IsRejected() throws Exception {
        String admin = login(adminLoginId);

        createCoupon(admin, couponBody("PERCENT", 101, "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());

        // ⚠ 100% 는 «전액 할인» 이라 뜻이 있다 — 막지 않는다.
        createCoupon(admin, couponBody("PERCENT", 100, "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("⚠ 정액 할인이 최소주문금액보다 커도 **막지 않는다** — 「퍼주는 쿠폰」이지 틀린 값이 아니다")
    void fixedDiscountAboveMinOrderIsAllowedOnPurpose() throws Exception {
        String admin = login(adminLoginId);

        // 2026-09-10 실측: 운영에 이런 쿠폰이 1건 있다(가입 쿠폰 5,000원 / 최소주문 1,000원).
        // 🔴 막으면 정당한 프로모션이 막힌다. ⚠ 다만 1,000원 주문에 쓰면 4,000원이 소멸하므로
        //    «최대 얼마까지 쓰인다» 를 화면이 알려 주는 것이 맞는 자리다 — 막을 자리가 아니다.
        //    이 시험은 그 **결정을 못박는다**(다음 사람이 «구멍» 으로 보고 막지 않도록).
        mockMvc.perform(post("/api/admin/coupons").header("Authorization", admin).contentType(JSON)
                        .content("{\"name\":\"ZZ 퍼주는\",\"discountType\":\"FIXED\",\"discountValue\":5000,"
                               + "\"minOrderAmount\":1000,"
                               + "\"validFrom\":\"2026-01-01T00:00:00Z\",\"validUntil\":\"2027-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk());
    }
}
