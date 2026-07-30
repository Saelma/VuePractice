package com.glassvue.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발급시각 컷오프(E-2) — <b>이미 나가 있는 access 토큰</b>이 정지·강등·비밀번호 변경 시 즉시 죽는가.
 *
 * <p>왜 필요했나(2026-07-30 실측): 정지는 refresh 만 지웠고 <b>강등은 그것조차 안 했다.</b> 역할이 JWT
 * 클레임에 박혀 있으므로 <b>강등된 관리자가 access 만료까지(최대 30분) 관리자 권한을 계속 썼다.</b>
 *
 * <p>⚠ 이 테스트의 핵심은 <b>죽는 것과 사는 것을 함께 보는 것</b>이다. "무효화했다"만 보면 컷오프가
 * 너무 넓어 남의 토큰·새 토큰까지 죽이는 경우를 못 잡는다 — 그건 전 사용자 로그아웃이라 더 큰 사고다.
 *
 * <p>⚠ Redis 는 트랜잭션 밖이라 롤백이 안 지켜 준다 — 컷오프 키는 {@code @AfterEach} 로 지운다
 * (WA §3: 트랜잭션 밖의 것은 정리·격리로 다룬다. 테스트 Redis db 가 운영과 분리돼 있는 것과 짝).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TokenRevocationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TokenRevocationStore revocationStore;
    @Autowired StringRedisTemplate redis;

    private static final String PW = "password123";

    private String superLoginId;
    private String adminLoginId;
    private String userLoginId;
    private UUID adminId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        superLoginId = "zzrevsuper_" + suffix();
        adminLoginId = "zzrevadm_" + suffix();
        userLoginId = "zzrevuser_" + suffix();
        member(superLoginId, "ZZ컷오프최상위" + suffix(), Role.SUPER_ADMIN);
        adminId = member(adminLoginId, "ZZ컷오프관리자" + suffix(), Role.ADMIN);
        userId = member(userLoginId, "ZZ컷오프일반" + suffix(), Role.USER);
    }

    @AfterEach
    void tearDown() {
        redis.delete("auth:revoked-before:" + adminId);
        redis.delete("auth:revoked-before:" + userId);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role).build()).getId();
    }

    private String login(String loginId) throws Exception {
        return login(loginId, PW);
    }

    /**
     * 다음 초 경계까지 기다린다.
     *
     * <p>⚠ 이게 왜 필요한가: 컷오프 비교가 <b>초 단위 fail-closed</b>({@code iat <= cutoff})라,
     * 컷오프와 <b>같은 초에 새로 로그인하면 그 새 토큰도 무효</b>가 된다. 현실에서는 문제가 아니다 —
     * 정지 해제나 비밀번호 변경 뒤 사람이 다시 로그인하기까지 1초 이상 걸린다. <b>테스트만 같은 초에
     * 몰린다.</b> 그래서 이건 버그를 가리는 sleep 이 아니라, 실제 시간 간격을 재현하는 것이다.
     */
    private static void awaitNextSecond() throws InterruptedException {
        long now = System.currentTimeMillis();
        Thread.sleep(1000 - (now % 1000) + 50);
    }

    private String login(String loginId, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    // ---------- 강등 (E-2 의 원래 자리) ----------

    @Test
    @DisplayName("강등하면 그 관리자의 옛 access 토큰이 즉시 죽는다 — 관리 API 401")
    void demotion_killsExistingAdminToken() throws Exception {
        String adminToken = login(adminLoginId);
        // 강등 전에는 관리자 API 가 열린다(대조군 — 이게 없으면 401 이 다른 이유일 수 있다)
        mockMvc.perform(get("/api/admin/members").header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/members/" + adminId + "/role")
                        .header("Authorization", login(superLoginId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"));

        // 같은 토큰인데 이제 인증 자체가 안 세워진다 → 401(403 이 아니다: 토큰이 죽었으므로)
        mockMvc.perform(get("/api/admin/members").header("Authorization", adminToken))
                .andExpect(status().isUnauthorized());
        // 관리자 API 만이 아니라 일반 보호 경로도 막힌다(토큰 자체가 무효라는 뜻)
        mockMvc.perform(get("/api/auth/me").header("Authorization", adminToken))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 정지 ----------

    @Test
    @DisplayName("정지하면 그 회원의 옛 access 토큰이 즉시 죽고, 해제 후 재로그인은 통한다")
    void suspension_killsTokenButLoginRecoversAfterUnsuspend() throws Exception {
        String userToken = login(userLoginId);
        mockMvc.perform(get("/api/auth/me").header("Authorization", userToken))
                .andExpect(status().isOk());

        String admin = login(adminLoginId);
        mockMvc.perform(post("/api/admin/members/" + userId + "/suspend").header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization", userToken))
                .andExpect(status().isUnauthorized());

        // 해제 후 새로 받은 토큰은 컷오프에 걸리지 않는다 — 걸리면 해제해도 못 들어온다
        mockMvc.perform(post("/api/admin/members/" + userId + "/unsuspend").header("Authorization", admin))
                .andExpect(status().isOk());
        awaitNextSecond();
        mockMvc.perform(get("/api/auth/me").header("Authorization", login(userLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value(userLoginId));
    }

    @Test
    @DisplayName("컷오프는 그 회원만 — 다른 회원의 토큰은 살아 있다")
    void revocation_isPerMember() throws Exception {
        String userToken = login(userLoginId);
        String adminToken = login(adminLoginId);

        mockMvc.perform(post("/api/admin/members/" + userId + "/suspend")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", userToken))
                .andExpect(status().isUnauthorized());
        // 정지시킨 관리자 자신은 멀쩡해야 한다(컷오프가 넓게 잡혔으면 여기서 걸린다)
        mockMvc.perform(get("/api/auth/me").header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    // ---------- 비밀번호 변경 ----------

    @Test
    @DisplayName("비밀번호를 바꾸면 다른 기기의 옛 access 토큰이 죽는다")
    void passwordChange_killsOtherDeviceToken() throws Exception {
        String otherDevice = login(userLoginId); // 다른 기기에 남아 있는 토큰
        String thisDevice = login(userLoginId);

        mockMvc.perform(patch("/api/members/me/password").header("Authorization", thisDevice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PW + "\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", otherDevice))
                .andExpect(status().isUnauthorized());
        // ⚠ 현재 기기 토큰도 함께 죽는다 — 컷오프는 시각 기준이라 "이 토큰만 살려두기"가 안 된다.
        // 비밀번호를 바꾸는 이유가 보통 "남이 쓰고 있다" 이므로 이쪽이 안전한 기본값이다(프론트는 401 →
        // refresh 실패 → 로그인 화면으로 보낸다).
        mockMvc.perform(get("/api/auth/me").header("Authorization", thisDevice))
                .andExpect(status().isUnauthorized());
        // 바꾼 비밀번호로 다시 로그인하면 정상
        awaitNextSecond();
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", login(userLoginId, "newpassword123")))
                .andExpect(status().isOk());
    }

    // ---------- 컷오프 경계 ----------

    @Test
    @DisplayName("컷오프 경계는 fail-closed: 같은 초에 발급된 토큰도 무효, 이후 발급분은 유효")
    void cutoffBoundary_isFailClosed() {
        assertThat(revocationStore.isRevoked(userId, Instant.now())).isFalse(); // 컷오프 없음 → 통과

        revocationStore.revokeAll(userId);
        // ⚠ 컷오프를 Instant.now() 로 다시 만들지 않는다 — 초가 넘어가면 경계가 어긋나 간헐 실패한다.
        // 저장된 값을 그대로 읽어 경계를 잡는다(플래키 테스트를 만들지 않는 쪽).
        Instant cutoff = Instant.ofEpochSecond(
                Long.parseLong(redis.opsForValue().get("auth:revoked-before:" + userId)));

        assertThat(revocationStore.isRevoked(userId, cutoff.minusSeconds(5))).isTrue();
        // ⚠ 같은 초(iat == cutoff)도 무효다. < 로 비교하면 강등과 같은 초에 발급된 토큰이 살아남는다.
        assertThat(revocationStore.isRevoked(userId, cutoff)).isTrue();
        assertThat(revocationStore.isRevoked(userId, cutoff.plusSeconds(2))).isFalse();
        // 남의 컷오프는 남의 것
        assertThat(revocationStore.isRevoked(adminId, cutoff.minusSeconds(5))).isFalse();
    }
}
