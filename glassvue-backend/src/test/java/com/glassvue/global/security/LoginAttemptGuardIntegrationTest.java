package com.glassvue.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 무차별 대입 방어 (E-1) — 아이디 5회 / IP 20회, 10분 창.
 *
 * <p>⚠ <b>이 테스트는 자기만의 `X-Real-IP` 를 보낸다.</b> MockMvc 는 헤더가 없으면 전부
 * {@code 127.0.0.1} 로 잡히는데, 여기서 일부러 임계값을 넘기므로 그 버킷을 오염시키면
 * <b>같은 실행의 다른 테스트가 429 로 깨진다</b>(의도적 로그인 실패를 쓰는 테스트가 실제로 있다).
 * 테스트마다 IP 를 다르게 두는 게 곧 격리다 — WA §3 의 "정리가 아니라 격리" 와 같은 판단이다.
 *
 * <p>⚠ Redis 는 트랜잭션 밖이라 롤백이 안 지켜 준다 → 쓴 키는 {@code @AfterEach} 로 지운다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginAttemptGuardIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired StringRedisTemplate redis;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private static final String PW = "password123";

    private String loginId;
    /** 이 테스트 인스턴스 전용 IP — 다른 테스트·다른 케이스와 카운터를 섞지 않는다. */
    private String ip;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        loginId = "zzguard_" + suffix;
        ip = "203.0.113." + (1 + new java.util.Random(suffix.hashCode()).nextInt(250)); // TEST-NET-3
        memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname("ZZ가드" + suffix).role(Role.USER).build());
    }

    @AfterEach
    void tearDown() {
        redis.delete("auth:login-fail:ip:" + ip);
        redis.keys("auth:login-fail:id:zzguard_*").forEach(redis::delete);
        redis.keys("auth:login-fail:id:zznone_*").forEach(redis::delete);
    }

    /** 로그인 시도. 실제 프론트처럼 nginx 가 심는 `X-Real-IP` 를 함께 보낸다. */
    private int attempt(String id, String password, String fromIp) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .header("X-Real-IP", fromIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + id + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    // ---------- 아이디 기준 ----------

    @Test
    @DisplayName("아이디 5회 실패 후 429 — ⚠ 그때는 비밀번호가 맞아도 막힌다")
    void blocksAfterFiveFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(attempt(loginId, "wrong-password", ip)).as("실패 %d회차", i + 1).isEqualTo(401);
        }
        // ⚠ 이 줄이 이 기능의 전부다 — **맞는 비밀번호로도 막혀야** 무차별 대입이 실제로 끊긴다.
        // 401 이 나오면 카운트만 하고 차단은 안 하는 상태다(있으나 없으나인 방어).
        mockMvc.perform(post("/api/auth/login").header("X-Real-IP", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("AUTH-429"));
    }

    @Test
    @DisplayName("없는 아이디도 5회 후 429 — 차단 응답이 계정 존재를 알려주지 않는다")
    void blocksUnknownIdTheSameWay() throws Exception {
        String unknown = "zznone_" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 5; i++) {
            assertThat(attempt(unknown, "whatever", ip)).isEqualTo(401);
        }
        // ⚠ 존재하는 계정과 **같은 응답**이어야 한다. 여기서 401 이 계속 나오면(=없는 아이디는 안 세면)
        // 429 라는 사실 자체가 "그 아이디는 있다" 는 신호가 된다.
        assertThat(attempt(unknown, "whatever", ip)).isEqualTo(429);
    }

    @Test
    @DisplayName("대소문자를 바꿔도 같은 카운터 — 우회 불가")
    void caseInsensitiveCounter() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(attempt(loginId, "wrong", ip)).isEqualTo(401);
        }
        for (int i = 0; i < 2; i++) {
            assertThat(attempt(loginId.toUpperCase(java.util.Locale.ROOT), "wrong", ip)).isEqualTo(401);
        }
        // 3 + 2 = 5 → 대소문자를 섞었어도 합산돼 차단된다
        assertThat(attempt(loginId, PW, ip)).isEqualTo(429);
    }

    @Test
    @DisplayName("성공하면 아이디 카운터가 리셋된다")
    void successResetsIdCounter() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertThat(attempt(loginId, "wrong", ip)).isEqualTo(401);
        }
        assertThat(attempt(loginId, PW, ip)).as("4회는 아직 차단 전").isEqualTo(200);
        // 리셋됐으므로 다시 4회를 틀려도 차단되지 않는다(리셋이 안 되면 5회째에서 429)
        for (int i = 0; i < 4; i++) {
            assertThat(attempt(loginId, "wrong", ip)).isEqualTo(401);
        }
        assertThat(attempt(loginId, PW, ip)).isEqualTo(200);
    }

    // ---------- IP 기준 ----------

    @Test
    @DisplayName("한 IP 가 여러 아이디를 훑으면 IP 기준(20회)으로 막힌다")
    void blocksIpScanningManyIds() throws Exception {
        String scanIp = "198.51.100.7"; // TEST-NET-2 — 위 케이스들과 다른 버킷
        try {
            for (int i = 0; i < 20; i++) {
                // 아이디를 매번 바꾸므로 **아이디 카운터는 각 1회**다 — 그래도 IP 예산이 소진돼야 한다
                String scanned = "zznone_scan" + i + "_" + UUID.randomUUID().toString().substring(0, 4);
                assertThat(attempt(scanned, "wrong", scanIp)).as("%d번째 아이디", i + 1).isEqualTo(401);
            }
            // 21번째는 처음 보는 아이디인데도 막힌다
            assertThat(attempt("zznone_scan_last", "wrong", scanIp)).isEqualTo(429);
            // ⚠ 그리고 **정상 계정의 로그인까지** 그 IP 에서는 막힌다(공격 IP 를 끊는다는 뜻)
            assertThat(attempt(loginId, PW, scanIp)).isEqualTo(429);
            // 다른 IP 에서는 멀쩡하다 — IP 차단이 전역이 아니라는 것(이게 깨지면 전 사용자 잠금이다)
            assertThat(attempt(loginId, PW, "198.51.100.200")).isEqualTo(200);
        } finally {
            redis.delete("auth:login-fail:ip:" + scanIp);
            redis.delete("auth:login-fail:ip:198.51.100.200");
            redis.keys("auth:login-fail:id:zznone_scan*").forEach(redis::delete);
        }
    }
}
