package com.glassvue.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 *
 * <p>🔴 <b>알려진 한계 — 운영에 이벤트가 「진행 중」이면 이 클래스의 일부가 못 돈다</b>(2026-08-13).
 * 「지금 열린 이벤트」를 만드는 테스트들은 발급 창이 <b>전역에서 하나뿐</b>이라는 규칙에 걸려
 * 등록 자체가 400(겹침)으로 거부된다. 롤백은 <b>내가 만든 것</b>만 되돌리지 <b>이미 커밋된 운영
 * 데이터</b>는 어쩌지 못한다. ⚠ 이건 테스트를 고쳐서 없앨 수 있는 문제가 아니라 <b>설계(겹침 금지)와
 * 공유 DB가 만나는 자리</b>다 — 테스트 전용 스키마가 생기기 전까지는 남는다.
 * → 배너 관련 둘은 그래도 «기준선을 먼저 읽는» 방식으로 견디게 고쳤다(아래 각 테스트 주석).
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

    /**
     * 🔴 <b>지금 열려 있는 이벤트를 「확보」한다 — 없으면 만들고, 있으면 그걸 쓴다.</b>
     *
     * <p>⚠ 무조건 만들면 <b>운영에 이벤트가 진행 중일 때 겹침 금지에 걸려 400</b> 이 난다.
     * 2026-08-13 검증 중 실제로 그랬다 — 첫 이벤트를 띄운 순간 이 클래스의 절반이 빨개졌다.
     * 롤백은 내가 만든 것만 되돌리지 <b>이미 커밋된 운영 데이터</b>는 못 없앤다.
     *
     * <p>⚠ 그래서 «내가 만든 쿠폰이 온다» 를 단언하지 않고 <b>«열린 이벤트가 하나 있고, 그것을
     * 받으면 이렇게 된다»</b> 를 단언한다. 계약은 그쪽이고, 이름은 계약이 아니다.
     *
     * @return 지금 열려 있는 이벤트의 쿠폰명
     */
    private String openEventName(String admin, String user) throws Exception {
        String body = mockMvc.perform(get("/api/coupons/event").header("Authorization", user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Object data = JsonPath.read(body, "$.data");
        if (data != null && (Boolean) JsonPath.read(body, "$.data.open")) {
            return JsonPath.read(body, "$.data.name");
        }
        Instant now = Instant.now();
        // 발급 창은 오늘 하루(±1h), 사용 기간은 한 달 — 이 «다름» 이 이 기능의 요점이다.
        String name = "ZZ이벤트 " + UUID.randomUUID().toString().substring(0, 8);
        createCoupon(admin, name, now.minus(1, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.HOURS), now.plus(30, ChronoUnit.DAYS)).andExpect(status().isOk());
        return name;
    }

    @Test
    @DisplayName("오늘 열린 이벤트 — 배너가 open 으로 오고, 받으면 claimed 로 바뀌며 쿠폰함에 들어온다")
    void claimOpenEvent() throws Exception {
        String admin = login(adminLoginId);
        String user = login(userLoginId);
        String name = openEventName(admin, user);

        mockMvc.perform(get("/api/coupons/event").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.open").value(true))
                .andExpect(jsonPath("$.data.claimed").value(false))
                .andExpect(jsonPath("$.data.daysUntil").doesNotExist());

        mockMvc.perform(post("/api/coupons/event/claim").header("Authorization", user))
                .andExpect(status().isOk());

        // 「받음」으로 확정되는 근거 — 화면은 이 값으로 버튼을 그린다.
        mockMvc.perform(get("/api/coupons/event").header("Authorization", user))
                .andExpect(jsonPath("$.data.claimed").value(true));

        // 받은 쿠폰은 발급 창이 닫힌 뒤에도 쓸 수 있어야 한다 — 쿠폰함에 실제로 들어와 있다.
        // ⚠ 이 사용자는 오늘 만들어졌으므로 쿠폰함에 이것 하나뿐이다.
        mockMvc.perform(get("/api/coupons/me?itemsTotal=10000").header("Authorization", user))
                .andExpect(jsonPath("$.data[0].name").value(name))
                .andExpect(jsonPath("$.data[0].usable").value(true));
    }

    @Test
    @DisplayName("두 번째 「받기」는 409 — 회원당 한 장")
    void claimTwice() throws Exception {
        String admin = login(adminLoginId);
        String user = login(userLoginId);
        openEventName(admin, user);

        mockMvc.perform(post("/api/coupons/event/claim").header("Authorization", user)).andExpect(status().isOk());
        mockMvc.perform(post("/api/coupons/event/claim").header("Authorization", user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COUPON-409I"));
    }

    /**
     * 발급 창이 닫힌 이벤트의 쿠폰은 뒤늦게 못 받는다.
     *
     * <p>⚠ «claim 이 400 이다» 로 단언하지 않는다 — <b>다른 이벤트가 열려 있으면 그건 200</b> 이고,
     * 그래도 이 테스트가 볼 것(닫힌 쿠폰이 안 나갔다)은 참이다. 그래서 <b>쿠폰함에 그것이 없다</b>로
     * 단언한다. 열린 것이 하나도 없을 때만 400 까지 확인한다.
     */
    @Test
    @DisplayName("발급 창이 닫혀 있으면 그 쿠폰은 안 나간다 — 열린 게 없으면 400")
    void claimClosedEvent() throws Exception {
        String admin = login(adminLoginId);
        String user = login(userLoginId);
        Instant now = Instant.now();

        // 이미 닫힌 하루짜리 이벤트. ⚠ 사용 기간은 아직 살아 있다 — «발급은 끝났지만 쓸 수는 있다» 가 정상이다.
        //
        // 🔴 **창을 «어제» 에서 «한 해 전» 으로 옮겼다**(2026-08-14). 어제 창은 **운영과 겹쳐 생성이
        //    거부됐다** — 08-13 검증 쿠폰(발급 창 08-13)이 남아 있었고, 겹침 금지는 앱이 지키므로
        //    createCoupon 자체가 실패해 **테스트가 볼 것에 닿지도 못했다.**
        // ⚠ 한 해 전이 안전한 것은 우연이 아니다: **이벤트 쿠폰 기능은 2026-08-13 에 생겼다**(G-8, V49).
        //    그 이전 발급 창을 가진 쿠폰은 **구조적으로 있을 수 없다** — «아마 없겠지» 가 아니다.
        //    (근본은 그대로다: 공유 DB라 운영 상태를 전제하는 단언은 언젠가 깨진다 — 08-13 §9-4.)
        String closed = "ZZ지난해 이벤트 " + UUID.randomUUID().toString().substring(0, 8);
        createCoupon(admin, closed, now.minus(400, ChronoUnit.DAYS),
                now.minus(399, ChronoUnit.DAYS), now.plus(20, ChronoUnit.DAYS)).andExpect(status().isOk());

        String banner = mockMvc.perform(get("/api/coupons/event").header("Authorization", user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Object data = JsonPath.read(banner, "$.data");
        boolean somethingOpen = data != null && (Boolean) JsonPath.read(banner, "$.data.open");

        if (somethingOpen) {
            mockMvc.perform(post("/api/coupons/event/claim").header("Authorization", user))
                    .andExpect(status().isOk());
        } else {
            mockMvc.perform(post("/api/coupons/event/claim").header("Authorization", user))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COUPON-400C"));
        }

        // 어느 쪽이든 **닫힌 이벤트의 쿠폰은 쿠폰함에 없어야** 한다.
        String box = mockMvc.perform(get("/api/coupons/me").header("Authorization", user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(box, "$.data[*].name")).doesNotContain(closed);
    }

    /**
     * 예고 — 앞으로 있을 이벤트는 {@code open=false} 에 {@code daysUntil}(KST)이 실린다.
     *
     * <p>🔴 <b>이 테스트는 운영 데이터에 기대면 안 된다.</b> 배너는 «가장 가까운 것 하나» 를 돌려주므로,
     * 운영에 이벤트 쿠폰이 <b>하나라도 커밋돼 있으면</b> 내가 만든 것이 안 돌아올 수 있다.
     * ⚠ 처음엔 «내 쿠폰이 D-3 으로 온다» 로 썼다가 2026-08-13 검증 잔재(`ZZ-이벤트쿠폰3`, D-1)에
     * <b>실제로 깨졌다</b> — 그때 테스트가 잡은 것은 버그가 아니라 <b>제 가정이 틀렸다는 사실</b>이었다.
     *
     * <p>→ 그래서 <b>기준선을 먼저 읽는다.</b> 비어 있었으면 내 것이 정확히 와야 하고(엄밀한 단언),
     * 이미 무언가 있었으면 «더 가까운 것이 온다» 는 <b>불변식</b>을 단언한다. 뒤엣것도 진짜 계약이다 —
     * 배너의 규칙이 «가장 가까운 이벤트» 이기 때문이다.
     */
    @Test
    @DisplayName("예고 — 앞으로 있을 이벤트는 open=false 에 daysUntil(KST) 이 실린다")
    void upcomingEventBanner() throws Exception {
        String admin = login(adminLoginId);
        String user = login(userLoginId);

        String baseline = mockMvc.perform(get("/api/coupons/event").header("Authorization", user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Object existing = JsonPath.read(baseline, "$.data");

        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        createCoupon(admin, "ZZ다음주 이벤트", start, start.plus(1, ChronoUnit.HOURS),
                start.plus(30, ChronoUnit.DAYS)).andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/coupons/event").header("Authorization", user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        if (existing == null) {
            // ⚠ D-day 는 «날짜의 차» 라 서버가 KST 로 센다. 3일 뒤 같은 시각이면 경계를 어떻게 넘든 3 이다.
            assertThat((String) JsonPath.read(body, "$.data.name")).isEqualTo("ZZ다음주 이벤트");
            assertThat((Boolean) JsonPath.read(body, "$.data.open")).isFalse();
            assertThat((Boolean) JsonPath.read(body, "$.data.claimed")).isFalse();
            assertThat((Integer) JsonPath.read(body, "$.data.daysUntil")).isEqualTo(3);
        } else {
            // 더 가까운 것이 이미 있었다 — 그렇다면 배너는 그것을 보여줘야 하고, 내 D-3 보다 가깝다.
            boolean open = JsonPath.read(body, "$.data.open");
            assertThat(open || (Integer) JsonPath.read(body, "$.data.daysUntil") <= 3).isTrue();
        }
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

        // ⚠ «data 가 null 이다» 로 단언하지 않는다 — 운영에 이벤트 쿠폰이 하나라도 있으면 그게 실려 온다
        //    (2026-08-13 검증 잔재에 실제로 깨졌다). 여기서 볼 것은 **상시 쿠폰이 배너에 안 온다**는 것뿐이다.
        String body = mockMvc.perform(get("/api/coupons/event").header("Authorization", user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Object data = JsonPath.read(body, "$.data");
        if (data != null) {
            assertThat((String) JsonPath.read(body, "$.data.name")).isNotEqualTo("ZZ상시 쿠폰");
        }
    }

    @Test
    @DisplayName("권한 — 배너 GET 은 비로그인도 200(claimed=false) / 받기 POST 는 401")
    void permissions() throws Exception {
        String admin = login(adminLoginId);
        // ⚠ 열린 이벤트가 이미 있으면 그걸 쓴다 — 무조건 만들면 겹침 금지에 걸린다(openEventName 주석).
        openEventName(admin, login(userLoginId));

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

        // ⚠ **먼 미래의 달**에 만들고 그 달을 조회한다 — 이번 달에 만들면 운영에서 도는 이벤트와
        //    발급 창이 겹쳐 등록부터 막힌다(2026-08-13에 실제로 그랬다). 달을 비켜 가면 충돌이 없다.
        Instant far = Instant.now().plus(400, ChronoUnit.DAYS);
        String month = LocalDate.ofInstant(far, ZoneId.of("Asia/Seoul")).withDayOfMonth(15).toString().substring(0, 7);
        Instant start = LocalDate.ofInstant(far, ZoneId.of("Asia/Seoul")).withDayOfMonth(15)
                .atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();

        createCoupon(admin, "ZZ달력 이벤트", start, start.plus(1, ChronoUnit.DAYS), start.plus(30, ChronoUnit.DAYS))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/coupons/calendar?month=" + month).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daysInMonth").isNumber())
                .andExpect(jsonPath("$.data.firstDayOfWeek").isNumber())
                .andExpect(jsonPath("$.data.spans[?(@.name == 'ZZ달력 이벤트' && @.kind == 'ISSUE')]").exists())
                .andExpect(jsonPath("$.data.spans[?(@.name == 'ZZ달력 이벤트' && @.kind == 'USE')]").exists());

        // 상시 쿠폰은 사용 기간 하나뿐이다 — 발급 창이라는 개념이 없다.
        createCoupon(admin, "ZZ달력 상시", start, null, start.plus(30, ChronoUnit.DAYS))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/coupons/calendar?month=" + month).header("Authorization", admin))
                .andExpect(jsonPath("$.data.spans[?(@.name == 'ZZ달력 상시' && @.kind == 'ISSUE')]").doesNotExist())
                .andExpect(jsonPath("$.data.spans[?(@.name == 'ZZ달력 상시' && @.kind == 'USE')]").exists())
                // 🔴 화면은 이 플래그로 «격자에 그릴지 위 스트립으로 뺄지» 를 가른다(2026-08-13).
                //    ⚠ «ISSUE 막대가 있나» 로 유추하면 발급 창이 지난달인 이벤트를 상시로 잘못 분류한다.
                .andExpect(jsonPath("$.data.spans[?(@.name == 'ZZ달력 상시')].event")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))))
                .andExpect(jsonPath("$.data.spans[?(@.name == 'ZZ달력 이벤트')].event")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
    }

    /**
     * 「다음이 또 있다」 (2026-08-13, 사용자 요청).
     *
     * <p>배너가 하나만 보여주면 «이번을 놓치면 끝» 처럼 읽힌다. 쿠폰의 목적이 <b>다시 오게 하는 것</b>이라
     * 다음 약속이 화면에 있어야 한다. ⚠ 목록으로 늘어놓지는 않는다 — <b>개수와 가장 가까운 하나</b>만.
     */
    @Test
    @DisplayName("앞으로 더 있으면 moreUpcoming 과 nextDaysUntil 이 실린다")
    void bannerTellsAboutTheNextOne() throws Exception {
        String admin = login(adminLoginId);
        String user = login(userLoginId);

        // ⚠ 운영 이벤트와 안 겹치게 멀찍이 둘을 만든다. 배너가 무엇을 가리키든 «더 있다» 는 참이어야 한다.
        Instant first = Instant.now().plus(200, ChronoUnit.DAYS);
        createCoupon(admin, "ZZ먼이벤트1", first, first.plus(1, ChronoUnit.HOURS),
                first.plus(30, ChronoUnit.DAYS)).andExpect(status().isOk());
        Instant second = first.plus(10, ChronoUnit.DAYS);
        createCoupon(admin, "ZZ먼이벤트2", second, second.plus(1, ChronoUnit.HOURS),
                second.plus(30, ChronoUnit.DAYS)).andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/coupons/event").header("Authorization", user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        // 배너가 가리키는 것 말고 최소 둘(방금 만든 둘)이 더 있거나, 그중 하나를 가리키고 하나가 남는다.
        assertThat((Integer) JsonPath.read(body, "$.data.moreUpcoming")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) JsonPath.read(body, "$.data.nextDaysUntil")).isNotNull().isPositive();
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
