package com.glassvue.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.coupon.entity.Coupon;
import com.glassvue.domain.coupon.entity.DiscountType;
import com.glassvue.domain.coupon.repository.CouponRepository;
import com.glassvue.domain.member.event.MemberSignedUpEvent;
import com.glassvue.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 쿠폰 (G-2, 2026-07-31) — <b>가입 API 를 실제로 태워서</b> 확인한다.
 *
 * <p>단위 테스트({@code WelcomeCouponHandlerTest})가 발급 규칙을 보고, 여기서는 그 앞뒤를 본다:
 *
 * <ul>
 *   <li><b>가입 요청이 이벤트까지 도달하는가</b> — 컨트롤러·검증·비밀번호 정책을 다 지나야 한다.</li>
 *   <li><b>안내 API 가 비로그인에게 열려 있는가</b> — 닫혀 있으면 홈 스트립이 영영 문구를 못 띄운다.
 *       ⚠ 동시에 <b>`/api/coupons/me` 는 여전히 401</b> 이어야 한다(예외를 뚫으면서 옆칸까지 열면 안 된다).</li>
 * </ul>
 *
 * <p>⚠ 이벤트는 실제로 {@code @Async}+{@code AFTER_COMMIT} 으로 소비되는데 트랜잭션 테스트는 커밋을
 * 하지 않아 리스너가 안 뜬다. 그래서 <b>발행</b>만 여기서 보고 <b>소비</b>는 단위 테스트가 본다
 * (B-15·H-6 과 같은 방식).
 *
 * <p>가입 쿠폰 <b>지정도 관리자 API 로 한다</b>(V36) — 설정 파일이 아니라 데이터라서, 테스트도
 * 운영과 같은 경로(관리자가 지정 → 그때부터 발급·안내)를 그대로 탄다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Transactional
class WelcomeCouponIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired CouponRepository couponRepository;
    @Autowired WelcomeCouponHandler handler;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired ApplicationEvents events;

    private static final String JSON = "application/json";
    // ⚠ 가입 API 는 비밀번호 정책(E-3)을 탄다 — 10자 이상 + 흔한 목록·아이디/닉네임 포함 금지.
    private static final String PW = "Gv-welcome-2026";

    private String signup(String loginId) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\","
                                + "\"nickname\":\"ZZ가입" + loginId + "\",\"email\":\"" + loginId + "@example.com\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    /** 쿠폰을 만들고 **가입 쿠폰으로 지정**한다(지정은 리포지토리가 아니라 관리자 API 로). */
    private UUID designateWelcomeCoupon(String adminToken) throws Exception {
        Coupon coupon = couponRepository.save(Coupon.builder()
                .name("ZZ 가입축하 5천원").discountType(DiscountType.FIXED).discountValue(5_000L)
                .minOrderAmount(10_000L)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validUntil(Instant.now().plus(365, ChronoUnit.DAYS))
                .build());
        mockMvc.perform(post("/api/admin/coupons/" + coupon.getId() + "/welcome")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
        return coupon.getId();
    }

    private String adminToken() throws Exception {
        String loginId = "zzwa" + UUID.randomUUID().toString().substring(0, 6);
        memberRepository.save(com.glassvue.domain.member.entity.Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname("ZZ쿠폰관리자" + loginId)
                .role(com.glassvue.domain.member.entity.Role.ADMIN).build());
        return login(loginId);
    }

    private String login(String loginId) throws Exception {
        return "Bearer " + JsonPath.read(mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.data.accessToken");
    }

    @Test
    @DisplayName("가입 API → MemberSignedUpEvent 1건 (아이디가 실린다)")
    void signupPublishesEvent() throws Exception {
        String loginId = "zzwc" + UUID.randomUUID().toString().substring(0, 6);

        signup(loginId);

        assertThat(events.stream(MemberSignedUpEvent.class).count()).isEqualTo(1);
        assertThat(events.stream(MemberSignedUpEvent.class).findFirst().orElseThrow().loginId())
                .isEqualTo(loginId);
    }

    @Test
    @DisplayName("이벤트 소비 → 그 회원의 쿠폰함에 가입 쿠폰이 들어온다")
    void handlerIssuesWelcomeCoupon() throws Exception {
        designateWelcomeCoupon(adminToken());
        String loginId = "zzwc" + UUID.randomUUID().toString().substring(0, 6);
        signup(loginId);
        UUID memberId = memberRepository.findByLoginId(loginId).orElseThrow().getId();

        handler.handle(new MemberSignedUpEvent(memberId, loginId));

        mockMvc.perform(get("/api/coupons/me?itemsTotal=20000").header("Authorization", login(loginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("ZZ 가입축하 5천원"))
                .andExpect(jsonPath("$.data[0].usable").value(true));
    }

    @Test
    @DisplayName("⚠ 가입 쿠폰 안내는 **비로그인도** 볼 수 있다 — 그래야 홈이 문구를 띄운다")
    void welcomeEndpointIsPublic() throws Exception {
        designateWelcomeCoupon(adminToken());

        mockMvc.perform(get("/api/coupons/welcome"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("ZZ 가입축하 5천원"))
                .andExpect(jsonPath("$.data.discountValue").value(5000));
    }

    @Test
    @DisplayName("⚠ 예외를 뚫었어도 **내 쿠폰 목록은 여전히 401** — 옆칸까지 열리면 안 된다")
    void myCouponsStillRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/coupons/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("지정을 해제하면 안내가 사라진다 — **끄는 쪽도 재시작 없이** 즉시 반영")
    void clearingDesignationHidesTheOffer() throws Exception {
        String admin = adminToken();
        UUID couponId = designateWelcomeCoupon(admin);

        mockMvc.perform(delete("/api/admin/coupons/" + couponId + "/welcome").header("Authorization", admin))
                .andExpect(status().isOk());

        // data 가 비면 화면은 문구를 감춘다(없는 혜택을 광고하지 않는다).
        mockMvc.perform(get("/api/coupons/welcome"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("⚠ 지정은 **한 장만** — 새로 지정하면 이전 것은 자동 해제된다")
    void designatingAnotherClearsPrevious() throws Exception {
        String admin = adminToken();
        UUID first = designateWelcomeCoupon(admin);
        UUID second = designateWelcomeCoupon(admin);

        assertThat(couponRepository.findById(first).orElseThrow().isWelcome()).isFalse();
        assertThat(couponRepository.findById(second).orElseThrow().isWelcome()).isTrue();
        mockMvc.perform(get("/api/coupons/welcome"))
                .andExpect(jsonPath("$.data.id").value(second.toString()));
    }

    /**
     * ⚠ <b>DB 가 정말로 막는지</b>를 본다 — 서비스가 "기존 해제 후 지정" 을 하므로 앱 경로로는
     * 둘이 될 수 없지만, 그 방어는 <b>동시 지정에서 뚫린다</b>(서로의 미커밋 변경을 못 본다).
     * 그래서 V36 이 함수기반 유니크 인덱스를 걸었고, 여기서 <b>리포지토리로 직접</b> 우회해 확인한다.
     */
    @Test
    @DisplayName("⚠ 가입 쿠폰 둘은 **DB 가 거부**한다(ux_coupon_welcome)")
    void twoWelcomeCouponsAreRejectedByDb() throws Exception {
        designateWelcomeCoupon(adminToken());
        Coupon another = couponRepository.save(Coupon.builder()
                .name("ZZ 두번째").discountType(DiscountType.FIXED).discountValue(1_000L)
                .minOrderAmount(0L)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validUntil(Instant.now().plus(1, ChronoUnit.DAYS))
                .build());
        another.markWelcome(true);   // 서비스를 거치지 않고 직접 — 앱 방어를 우회한 상황

        assertThatThrownBy(() -> couponRepository.flush())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("지정 권한 — 비로그인 401 / 일반 회원 403 (§2-4)")
    void designatePermissions() throws Exception {
        String loginId = "zzwu" + UUID.randomUUID().toString().substring(0, 6);
        signup(loginId);
        UUID someCoupon = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/coupons/" + someCoupon + "/welcome"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/coupons/" + someCoupon + "/welcome")
                        .header("Authorization", login(loginId)))
                .andExpect(status().isForbidden());
    }
}
