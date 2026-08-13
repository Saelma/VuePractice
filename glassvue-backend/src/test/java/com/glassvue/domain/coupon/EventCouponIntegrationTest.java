package com.glassvue.domain.coupon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * 이벤트 쿠폰(G-8, V49) — 등록 검증 · 배너 · 「받기」.
 *
 * <p>🔴 <b>여기서 지키는 것은 «발급 창과 사용 기간은 다른 것» 이다.</b> 그 둘을 한 값으로 쓰면
 * «그 날 하루» 이벤트 쿠폰이 그 날 자정에 만료돼 <b>받자마자 못 쓴다</b> — 화면으로는 안 보이고
 * 고객이 쓰려는 순간에야 드러나는 종류의 고장이다.
 *
 * <p>⚠ 겹침 금지는 <b>DB 가 아니라 앱이 유일하게</b> 지킨다(Oracle 유니크로는 기간 겹침을 못 막는다).
 * 그래서 «겹치는 둘을 등록» 을 여기서 못 박는다 — 이 테스트가 그 규칙의 유일한 증거다.
 *
 * <p>동시 요청은 롤백 트랜잭션 안에서 못 만든다 → {@link EventCouponConcurrencyTest} 로 갈라 뒀다.
 *
 * <p>DB_HOST 있을 때만 실행, {@code @Transactional} 롤백으로 공유 DB 무오염.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventCouponIntegrationTest {

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
        userLoginId = "evuser_" + UUID.randomUUID().toString().substring(0, 8);
        adminLoginId = "evadmin_" + UUID.randomUUID().toString().substring(0, 8);
        userId = member(userLoginId, "ZZ이벤트유저", Role.USER);
        member(adminLoginId, "ZZ이벤트관리자", Role.ADMIN);
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

    /** 이벤트 쿠폰 등록. {@code issueUntil} 이 null 이면 지금까지와 같은 상시 쿠폰이다. */
    private org.springframework.test.web.servlet.ResultActions createCoupon(
            String admin, String name, Instant validFrom, Instant issueUntil, Instant validUntil) throws Exception {
        String issue = (issueUntil == null) ? "" : ",\"issueUntil\":\"" + issueUntil + "\"";
        return mockMvc.perform(post("/api/admin/coupons").header("Authorization", admin).contentType(JSON)
                .content("{\"name\":\"" + name + "\",\"discountType\":\"FIXED\",\"discountValue\":3000,"
                        + "\"minOrderAmount\":0,"
                        + "\"validFrom\":\"" + validFrom + "\",\"validUntil\":\"" + validUntil + "\"" + issue + "}"));
    }

    @Test
    @DisplayName("오늘 열린 이벤트 — 배너가 open 으로 오고, 받으면 claimed 로 바뀌며 쿠폰함에 들어온다")
    void claimOpenEvent() throws Exception {
        String admin = login(adminLoginId);
        String user = login(userLoginId);
        Instant now = Instant.now();

        // 발급 창은 오늘 하루(±1h), 사용 기간은 한 달 — 이 «다름» 이 이 기능의 요점이다.
        createCoupon(admin, "ZZ이벤트 3천원", now.minus(1, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.HOURS), now.plus(30, ChronoUnit.DAYS))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/coupons/event").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("ZZ이벤트 3천원"))
                .andExpect(jsonPath("$.data.open").value(true))
                .andExpect(jsonPath("$.data.claimed").value(false))
                .andExpect(jsonPath("$.data.daysUntil").doesNotExist());

        mockMvc.perform(post("/api/coupons/event/claim").header("Authorization", user))
                .andExpect(status().isOk());

        // 「받음」으로 확정되는 근거 — 화면은 이 값으로 버튼을 그린다.
        mockMvc.perform(get("/api/coupons/event").header("Authorization", user))
                .andExpect(jsonPath("$.data.claimed").value(true));

        // 받은 쿠폰은 발급 창이 닫힌 뒤에도 쓸 수 있어야 한다 — 쿠폰함에 실제로 들어와 있다.
        mockMvc.perform(get("/api/coupons/me?itemsTotal=10000").header("Authorization", user))
                .andExpect(jsonPath("$.data[0].name").value("ZZ이벤트 3천원"))
                .andExpect(jsonPath("$.data[0].usable").value(true))
                .andExpect(jsonPath("$.data[0].discountPreview").value(3000));
    }

    @Test
    @DisplayName("두 번째 「받기」는 409 — 회원당 한 장")
    void claimTwice() throws Exception {
        String admin = login(adminLoginId);
        String user = login(userLoginId);
        Instant now = Instant.now();
        createCoupon(admin, "ZZ이벤트 중복", now.minus(1, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.HOURS), now.plus(30, ChronoUnit.DAYS)).andExpect(status().isOk());

        mockMvc.perform(post("/api/coupons/event/claim").header("Authorization", user)).andExpect(status().isOk());
        mockMvc.perform(post("/api/coupons/event/claim").header("Authorization", user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COUPON-409I"));
    }

    @Test
    @DisplayName("발급 창이 닫혀 있으면 400 — 이미 지난 이벤트의 쿠폰은 뒤늦게 못 받는다")
    void claimClosedEvent() throws Exception {
        String admin = login(adminLoginId);
        String user = login(userLoginId);
        Instant now = Instant.now();

        // 어제 하루짜리 이벤트. ⚠ 사용 기간은 아직 살아 있다 — «발급은 끝났지만 쓸 수는 있다» 가 정상이다.
        createCoupon(admin, "ZZ어제 이벤트", now.minus(2, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS), now.plus(20, ChronoUnit.DAYS)).andExpect(status().isOk());

        mockMvc.perform(post("/api/coupons/event/claim").header("Authorization", user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COUPON-400C"));
    }

    @Test
    @DisplayName("예고 — 앞으로 있을 이벤트는 open=false 에 daysUntil(KST) 이 실린다")
    void upcomingEventBanner() throws Exception {
        String admin = login(adminLoginId);
        String user = login(userLoginId);
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);

        createCoupon(admin, "ZZ다음주 이벤트", start, start.plus(1, ChronoUnit.HOURS),
                start.plus(30, ChronoUnit.DAYS)).andExpect(status().isOk());

        // ⚠ D-day 는 «날짜의 차» 라 서버가 KST 로 센다. 3일 뒤 같은 시각이면 경계를 어떻게 넘든 3 이다.
        mockMvc.perform(get("/api/coupons/event").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.open").value(false))
                .andExpect(jsonPath("$.data.claimed").value(false))
                .andExpect(jsonPath("$.data.daysUntil").value(3));
    }

    @Test
    @DisplayName("발급 창이 겹치는 이벤트는 등록 자체가 거부된다 — DB 가 아니라 앱이 지키는 규칙")
    void overlappingEventRejected() throws Exception {
        String admin = login(adminLoginId);
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);

        createCoupon(admin, "ZZ겹침 원본", start, start.plus(1, ChronoUnit.DAYS),
                start.plus(30, ChronoUnit.DAYS)).andExpect(status().isOk());

        // 발급 창이 반나절 겹친다.
        createCoupon(admin, "ZZ겹침 도전", start.plus(12, ChronoUnit.HOURS), start.plus(2, ChronoUnit.DAYS),
                start.plus(30, ChronoUnit.DAYS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COUPON-400O"));
    }

    @Test
    @DisplayName("발급 창이 안 겹치면 등록된다 — 사용 기간은 겹쳐도 정상이다")
    void nonOverlappingEventsCoexist() throws Exception {
        String admin = login(adminLoginId);
        Instant first = Instant.now().plus(10, ChronoUnit.DAYS);

        createCoupon(admin, "ZZ8월 이벤트", first, first.plus(1, ChronoUnit.DAYS),
                first.plus(30, ChronoUnit.DAYS)).andExpect(status().isOk());

        // 🔴 사용 기간(각 30일)은 서로 겹친다. 여기서 막히면 두 번째 이벤트부터 영영 등록이 안 된다.
        Instant second = first.plus(5, ChronoUnit.DAYS);
        createCoupon(admin, "ZZ8월 두번째", second, second.plus(1, ChronoUnit.DAYS),
                second.plus(30, ChronoUnit.DAYS)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("사용 마감이 발급 마감보다 빠르면 거부 — 받자마자 만료된 쿠폰을 내보내지 않는다")
    void issueWindowOutlivingValidity() throws Exception {
        String admin = login(adminLoginId);
        Instant start = Instant.now().plus(20, ChronoUnit.DAYS);

        createCoupon(admin, "ZZ받자마자만료", start, start.plus(5, ChronoUnit.DAYS), start.plus(1, ChronoUnit.DAYS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COUPON-400W"));

        // 발급 창이 뒤집힌 경우(마감이 시작보다 앞) — 아무도 못 받는 이벤트가 조용히 등록되면 안 된다.
        createCoupon(admin, "ZZ뒤집힌창", start, start.minus(1, ChronoUnit.DAYS), start.plus(30, ChronoUnit.DAYS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COUPON-400W"));
    }

    @Test
    @DisplayName("상시 쿠폰은 배너에 안 뜬다 — issue_until 이 null 이면 이벤트가 아니다")
    void plainCouponIsNotAnEvent() throws Exception {
        String admin = login(adminLoginId);
        String user = login(userLoginId);
        Instant now = Instant.now();

        createCoupon(admin, "ZZ상시 쿠폰", now.minus(1, ChronoUnit.DAYS), null, now.plus(30, ChronoUnit.DAYS))
                .andExpect(status().isOk());

        // 줄 게 없으면 data:null — 화면은 «예정된 이벤트가 없습니다» 를 그리지 않고 배너 자체를 안 만든다.
        mockMvc.perform(get("/api/coupons/event").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("권한 — 배너 GET 은 비로그인도 200(claimed=false) / 받기 POST 는 401")
    void permissions() throws Exception {
        String admin = login(adminLoginId);
        Instant now = Instant.now();
        createCoupon(admin, "ZZ공개확인", now.minus(1, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.HOURS), now.plus(30, ChronoUnit.DAYS)).andExpect(status().isOk());

        // 비로그인 혜택 스트립이 «오늘 이벤트 중» 을 띄우려면 로그인 전에 읽혀야 한다.
        mockMvc.perform(get("/api/coupons/event"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.open").value(true))
                .andExpect(jsonPath("$.data.claimed").value(false));

        // 🔴 경로가 아니라 **메서드**로 뚫었다 — POST 까지 열리면 발급이 공개된다.
        mockMvc.perform(post("/api/coupons/event/claim")).andExpect(status().isUnauthorized());
    }

    /**
     * 프로모션 달력(B-27) — 권한 + <b>막대가 둘로 갈리는지</b>.
     *
     * <p>🔴 갈라지지 않으면 이 화면은 <b>거짓말을 한다</b> — 관리자가 정상인 사용 기간 겹침을
     * 「겹쳤다」로 읽는다. 그게 이 화면을 만든 이유의 정반대다.
     */
    @Test
    @DisplayName("프로모션 달력 — 비로그인 401 / USER 403 / ADMIN 200, 이벤트 쿠폰은 막대가 둘(ISSUE·USE)")
    void promotionCalendar() throws Exception {
        mockMvc.perform(get("/api/admin/coupons/calendar")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/coupons/calendar").header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());

        String admin = login(adminLoginId);
        Instant now = Instant.now();
        createCoupon(admin, "ZZ달력 이벤트", now.minus(1, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.HOURS), now.plus(30, ChronoUnit.DAYS)).andExpect(status().isOk());

        // month 를 비우면 이번 달(KST) — 방금 만든 이벤트가 이번 달에 걸린다.
        mockMvc.perform(get("/api/admin/coupons/calendar").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daysInMonth").isNumber())
                .andExpect(jsonPath("$.data.firstDayOfWeek").isNumber())
                .andExpect(jsonPath("$.data.spans[?(@.name == 'ZZ달력 이벤트' && @.kind == 'ISSUE')]").exists())
                .andExpect(jsonPath("$.data.spans[?(@.name == 'ZZ달력 이벤트' && @.kind == 'USE')]").exists());

        // 상시 쿠폰은 사용 기간 하나뿐이다 — 발급 창이라는 개념이 없다.
        createCoupon(admin, "ZZ달력 상시", now.minus(1, ChronoUnit.DAYS), null, now.plus(30, ChronoUnit.DAYS))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/coupons/calendar").header("Authorization", admin))
                .andExpect(jsonPath("$.data.spans[?(@.name == 'ZZ달력 상시' && @.kind == 'ISSUE')]").doesNotExist())
                .andExpect(jsonPath("$.data.spans[?(@.name == 'ZZ달력 상시' && @.kind == 'USE')]").exists());
    }

    @Test
    @DisplayName("관리자 발급도 회원당 한 장 — 두 번째는 500 이 아니라 409 로 답한다")
    void adminIssueTwice() throws Exception {
        String admin = login(adminLoginId);
        Instant now = Instant.now();
        String body = createCoupon(admin, "ZZ관리자발급", now.minus(1, ChronoUnit.DAYS), null,
                now.plus(30, ChronoUnit.DAYS)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String couponId = JsonPath.read(body, "$.data");

        mockMvc.perform(post("/api/admin/coupons/" + couponId + "/issue?memberId=" + userId)
                .header("Authorization", admin)).andExpect(status().isOk());

        // ⚠ 2026-08-13 에 바뀐 규칙이다 — 예전엔 여러 장 발급이 허용됐다(V49 주석).
        mockMvc.perform(post("/api/admin/coupons/" + couponId + "/issue?memberId=" + userId)
                        .header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COUPON-409I"));
    }
}
