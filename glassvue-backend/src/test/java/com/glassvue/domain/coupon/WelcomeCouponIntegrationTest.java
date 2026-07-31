package com.glassvue.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.coupon.entity.Coupon;
import com.glassvue.domain.coupon.entity.DiscountType;
import com.glassvue.domain.coupon.repository.CouponRepository;
import com.glassvue.domain.member.event.MemberSignedUpEvent;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.global.common.BaseTimeEntity;
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
import org.springframework.test.context.TestPropertySource;
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
 * <p>설정({@code coupon.welcome-coupon-id})은 <b>이 테스트 안에서만</b> 실제 값으로 바꾼다 —
 * 운영 기본값은 "비어 있음(기능 꺼짐)" 이라 그 상태로는 발급 경로를 못 본다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Transactional
@TestPropertySource(properties = "coupon.welcome-coupon-id=" + WelcomeCouponIntegrationTest.WELCOME_ID)
class WelcomeCouponIntegrationTest {

    /** 고정 UUID — 테스트가 이 id 로 쿠폰을 만들고, 설정도 같은 값을 가리키게 한다. */
    static final String WELCOME_ID = "0198f000-0000-7000-8000-00000000c001";

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired CouponRepository couponRepository;
    @Autowired WelcomeCouponHandler handler;
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

    private Coupon saveWelcomeCoupon() {
        Coupon coupon = Coupon.builder()
                .name("ZZ 가입축하 5천원").discountType(DiscountType.FIXED).discountValue(5_000L)
                .minOrderAmount(10_000L)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validUntil(Instant.now().plus(365, ChronoUnit.DAYS))
                .build();
        // 설정이 가리키는 id 와 같아야 하므로 id 를 지정해 저장한다(UUIDv7 자동 생성 대신).
        return couponRepository.save(withId(coupon, UUID.fromString(WELCOME_ID)));
    }

    /**
     * {@code BaseTimeEntity.id} 는 앱이 UUIDv7 로 자동 생성하고 setter 가 없다 —
     * <b>설정값이 가리키는 id 와 맞춰야</b> 하므로 여기서만 리플렉션으로 심는다.
     * ({@code isNew()} 는 {@code createdAt} 으로 판단하므로 id 를 미리 채워도 INSERT 로 나간다.)
     */
    private static Coupon withId(Coupon coupon, UUID id) {
        try {
            var field = BaseTimeEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(coupon, id);
            return coupon;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("테스트 쿠폰 id 주입 실패", e);
        }
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
        saveWelcomeCoupon();
        String loginId = "zzwc" + UUID.randomUUID().toString().substring(0, 6);
        signup(loginId);
        UUID memberId = memberRepository.findByLoginId(loginId).orElseThrow().getId();

        handler.handle(new MemberSignedUpEvent(memberId, loginId));

        String token = "Bearer " + JsonPath.read(mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andReturn().getResponse().getContentAsString(), "$.data.accessToken");

        mockMvc.perform(get("/api/coupons/me?itemsTotal=20000").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("ZZ 가입축하 5천원"))
                .andExpect(jsonPath("$.data[0].usable").value(true));
    }

    @Test
    @DisplayName("⚠ 가입 쿠폰 안내는 **비로그인도** 볼 수 있다 — 그래야 홈이 문구를 띄운다")
    void welcomeEndpointIsPublic() throws Exception {
        saveWelcomeCoupon();

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
}
