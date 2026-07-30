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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정 <b>요청</b> 제한 — 아이디 3회 / IP 10회 · 10분.
 *
 * <p>막는 것은 공격보다 <b>괴롭히기</b>다: 이 경로는 열거 방지를 위해 항상 200 이라, 남의 아이디만 알면
 * 그 사람 메일함에 재설정 메일을 무한히 보낼 수 있었다. 부수적으로 <b>유효한 링크가 쌓이는 것</b>도 막는다
 * (토큰이 회원당 하나가 아니라 <b>토큰당 하나</b>라 요청마다 30분짜리가 하나씩 더 생긴다).
 *
 * <p>⚠ <b>제한을 붙이면서 열거 방지를 깨뜨리지 않는지</b>가 이 테스트의 핵심이다 — 없는 아이디도
 * 똑같이 429 가 나와야 한다. 존재하는 계정만 세면 429 자체가 "그 계정은 있다" 는 신호가 된다.
 *
 * <p>⚠ 테스트마다 자기 IP 를 보낸다(WA §3) — 임계값을 넘기는 테스트가 공용 버킷을 쓰면 같은 실행의
 * 다른 테스트가 429 로 깨진다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetRequestGuardIntegrationTest {

    private static final String JSON = "application/json";

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;

    private String loginId;
    private String ip;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        loginId = "zzreset_" + suffix;
        ip = "203.0.113." + (100 + Math.abs(suffix.hashCode()) % 100);
        memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode("Tulip-Harbor-72"))
                .nickname("ZZ재설정" + suffix).role(Role.USER).build());
    }

    @AfterEach
    void tearDown() {
        // Redis 는 트랜잭션 밖이라 롤백이 안 지켜 준다 — 요청 카운터와 발급된 토큰을 함께 치운다.
        redis.keys("auth:reset-req:*").forEach(redis::delete);
        redis.keys("auth:reset:*").forEach(redis::delete);
    }

    private int request(String id, String fromIp) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/request")
                        .header("X-Real-IP", fromIp)
                        .contentType(JSON)
                        .content("{\"loginId\":\"" + id + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("같은 아이디로 3회는 200, 4회째 429(AUTH-429R)")
    void blocksAfterThreeRequests() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(request(loginId, ip)).as("%d회차", i + 1).isEqualTo(200);
        }
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .header("X-Real-IP", ip).contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("AUTH-429R"));
    }

    @Test
    @DisplayName("⚠ 없는 아이디도 3회 후 429 — 제한이 열거 방지를 깨뜨리지 않는다")
    void unknownIdIsThrottledTheSameWay() throws Exception {
        String unknown = "zznone_" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 3; i++) {
            // 없는 아이디도 열거 방지 때문에 200 이다(원래 설계)
            assertThat(request(unknown, ip)).isEqualTo(200);
        }
        // 그리고 제한도 똑같이 걸린다 — 여기서 200 이 계속 나오면 429 가 곧 "계정 있음" 신호가 된다
        assertThat(request(unknown, ip)).isEqualTo(429);
    }

    @Test
    @DisplayName("제한 중에는 새 토큰이 발급되지 않는다 (메일도 나가지 않는다)")
    void blockedRequestIssuesNoToken() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(request(loginId, ip)).isEqualTo(200);
        }
        int tokensAfterThree = redis.keys("auth:reset:*").size();

        assertThat(request(loginId, ip)).isEqualTo(429);

        // ⚠ 이게 이 기능의 목적이다 — 429 를 돌려주는 것보다 **메일이 안 나가는 것**이 본질이다.
        // 토큰 발급과 발송이 같은 자리에 있으므로, 토큰 수가 늘지 않으면 메일도 안 나갔다.
        assertThat(redis.keys("auth:reset:*")).hasSize(tokensAfterThree);
    }

    @Test
    @DisplayName("한 IP 가 여러 아이디로 훑으면 IP 기준(10회)으로 막힌다 / 다른 IP 는 정상")
    void blocksIpScanning() throws Exception {
        String scanIp = "198.51.100.31";
        try {
            for (int i = 0; i < 10; i++) {
                // 아이디를 매번 바꿔 **아이디 카운터는 각 1회** — 그래도 IP 예산이 소진돼야 한다
                assertThat(request("zznone_s" + i + "_" + UUID.randomUUID().toString().substring(0, 4), scanIp))
                        .as("%d번째", i + 1).isEqualTo(200);
            }
            assertThat(request("zznone_s_last", scanIp)).isEqualTo(429);
            // 실제 회원의 정상 요청도 그 IP 에서는 막힌다(공격 IP 를 끊는다는 뜻)
            assertThat(request(loginId, scanIp)).isEqualTo(429);
            // 다른 IP 에서는 멀쩡하다 — 전역 차단이 아니라는 것
            assertThat(request(loginId, "198.51.100.200")).isEqualTo(200);
        } finally {
            redis.keys("auth:reset-req:*").forEach(redis::delete);
        }
    }
}
