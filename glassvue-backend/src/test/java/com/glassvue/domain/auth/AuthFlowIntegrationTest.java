package com.glassvue.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.auth.service.AuthService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 전체 플로우 통합 — 실 컨텍스트 + MockMvc. 회원가입 → 로그인(토큰) → 토큰으로 인증 API 호출.
 * @Transactional 로 DB 롤백(공유 DB 무오염). 로그인 시 Redis 리프레시 키는 랜덤 회원이라 무해.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowIntegrationTest {

    // ⚠ 비밀번호 정책(E-3, 2026-07-30) 때문에 픽스처를 바꿨다 — password123 은 차단 목록에 있다.
    //    가입·비밀번호 변경 API 는 정책을 타므로, **API 로 만드는 계정**은 정책을 통과하는 값을 써야 한다.
    //    (리포지토리로 직접 저장하는 픽스처는 검증을 안 타므로 password123 을 그대로 쓴다.)
    private static final String PW = "Tulip-Harbor-72";

    @Autowired MockMvc mockMvc;
    // 재설정 토큰은 Redis에 저장되고 응답 노출은 dev 프로파일만 켜진다(테스트는 기본 프로파일).
    // 그래서 HTTP로 토큰을 못 받으니, 서비스로 직접 발급해 confirm 엔드포인트만 E2E로 검증한다.
    @Autowired AuthService authService;

    private static final String JSON = "application/json";
    private final String loginId = "it_" + UUID.randomUUID().toString().substring(0, 8);

    @Test
    @DisplayName("회원가입 → 로그인 → 토큰으로 공지 작성 → 조회")
    void fullFlow() throws Exception {
        // 1) 회원가입
        mockMvc.perform(post("/api/auth/signup").contentType(JSON).content(
                        "{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\",\"nickname\":\"통합테스터\",\"email\":\"" + loginId + "@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nickname").value("통합테스터"));

        // 2) 로그인 → accessToken 추출
        String loginBody = mockMvc.perform(post("/api/auth/login").contentType(JSON).content(
                        "{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(loginBody, "$.data.accessToken");

        // 3) 토큰으로 공지 작성(인증 필요) → id 추출
        String createBody = mockMvc.perform(post("/api/notices").header("Authorization", "Bearer " + token)
                        .contentType(JSON).content("{\"title\":\"통합테스트공지\",\"content\":\"본문\",\"pinned\":false}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String noticeId = JsonPath.read(createBody, "$.data");

        // 4) 작성된 공지 조회 — 작성자 nickname 이 토큰 사용자와 일치
        mockMvc.perform(get("/api/notices/" + noticeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("통합테스트공지"))
                .andExpect(jsonPath("$.data.author").value("통합테스터"));
    }

    @Test
    @DisplayName("토큰 없이 공지 작성 → 401")
    void createWithoutToken() throws Exception {
        mockMvc.perform(post("/api/notices").contentType(JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\",\"pinned\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀번호 재설정: 토큰 발급 → confirm → 옛 비번 401, 새 비번 로그인")
    void passwordResetFlow() throws Exception {
        // 1) 가입
        mockMvc.perform(post("/api/auth/signup").contentType(JSON).content(
                        "{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\",\"nickname\":\"재설정테스터\",\"email\":\"" + loginId + "@example.com\"}"))
                .andExpect(status().isCreated());

        // 2) 재설정 토큰 발급(서비스로 직접 — 응답 노출은 dev만)
        String token = authService.requestPasswordReset(loginId, "203.0.113.11").orElseThrow();

        // 3) confirm 엔드포인트로 새 비번 설정
        mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(JSON).content(
                        "{\"token\":\"" + token + "\",\"newPassword\":\"newpassword456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 4) 옛 비번은 실패
        mockMvc.perform(post("/api/auth/login").contentType(JSON).content(
                        "{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isUnauthorized());

        // 5) 새 비번으로 로그인 성공
        mockMvc.perform(post("/api/auth/login").contentType(JSON).content(
                        "{\"loginId\":\"" + loginId + "\",\"password\":\"newpassword456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("비밀번호 재설정: 무효 토큰 confirm → 400")
    void passwordResetInvalidToken() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(JSON).content(
                        "{\"token\":\"not-a-real-token\",\"newPassword\":\"newpassword456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH-400R"));
    }

    @Test
    @DisplayName("비밀번호 재설정 요청: 없는 아이디여도 200(열거 방지)")
    void passwordResetRequestUnknownId() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request").contentType(JSON).content(
                        "{\"loginId\":\"nobody_" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---------- 이메일 수집 (B-13) ----------

    private String signupBody(String id, String nickname, String email) {
        return "{\"loginId\":\"" + id + "\",\"password\":\"" + PW + "\",\"nickname\":\"" + nickname
                + "\",\"email\":\"" + email + "\"}";
    }

    @Test
    @DisplayName("가입: 이메일 누락 → 400 / 형식 오류 → 400 (신규 가입은 필수)")
    void signup_emailRequiredAndValidated() throws Exception {
        String id = "em_" + UUID.randomUUID().toString().substring(0, 8);
        // 누락 — DB 는 nullable 이지만 API 계층에서 막는다(@NotBlank)
        mockMvc.perform(post("/api/auth/signup").contentType(JSON).content(
                        "{\"loginId\":\"" + id + "\",\"password\":\"" + PW + "\",\"nickname\":\"ZZ이메일없음\"}"))
                .andExpect(status().isBadRequest());
        // 형식 오류
        mockMvc.perform(post("/api/auth/signup").contentType(JSON)
                        .content(signupBody(id, "ZZ형식오류", "not-an-email")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("가입: 이메일은 소문자로 정규화되어 저장되고, 대소문자만 다른 주소는 중복(409)")
    void signup_emailNormalizedAndUnique() throws Exception {
        String id = "em_" + UUID.randomUUID().toString().substring(0, 8);
        String upper = "ZZ_" + id.toUpperCase() + "@Example.COM";

        // 대문자로 보내도 응답·저장은 소문자다
        mockMvc.perform(post("/api/auth/signup").contentType(JSON)
                        .content(signupBody(id, "ZZ정규화" + id.substring(3), upper)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(upper.toLowerCase()));

        // ⚠ 여기가 정규화의 값이다 — Oracle UNIQUE 는 대소문자를 구분하므로, 정규화가 없으면
        // 아래 요청이 "다른 값"으로 통과해 같은 사람의 주소가 두 계정에 들어간다.
        mockMvc.perform(post("/api/auth/signup").contentType(JSON)
                        .content(signupBody(id + "b", "ZZ중복" + id.substring(3), upper.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEMBER-409E"));
    }

    // ---------- 아이디 찾기 (G-1, 2026-07-31) ----------

    /**
     * ⚠ <b>요청마다 자기 IP 를 만들어 보낸다</b>(WA §3 — 정리가 아니라 격리).
     *
     * <p>이 경로는 <b>IP 당 10회 · 10분</b> 제한이 걸려 있고 카운터는 Redis 에 TTL 로 남는다.
     * 고정 IP(헤더 없으면 전부 {@code 127.0.0.1})를 쓰면 <b>10분 안에 스위트를 몇 번 돌리는 순간
     * 뒷 테스트가 429 로 깨진다</b> — 원인이 자기 테스트에 없어서 찾기 어려운 종류다.
     * 문서용 대역이 254개뿐이라 실행마다 겹칠 수 있어, 카운터 키로만 쓰인다는 점을 이용해
     * {@code 10.x.y.z} 에서 뽑는다(1600만 조합 — 실행 간 충돌이 사실상 없다).
     */
    private static String uniqueIp() {
        int n = Math.abs(UUID.randomUUID().hashCode());
        return "10." + (n >> 16 & 0xFF) + "." + (n >> 8 & 0xFF) + "." + (n & 0xFF);
    }

    private static String findIdBody(String email) {
        return "{\"email\":\"" + email + "\"}";
    }

    @Test
    @DisplayName("아이디 찾기: 가입 안 된 주소여도 200 (열거 방지)")
    void findId_unknownEmail() throws Exception {
        mockMvc.perform(post("/api/auth/find-id").header("X-Real-IP", uniqueIp())
                        .contentType(JSON).content(findIdBody("nobody_" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("⚠ 아이디 찾기: 가입된 주소로 요청해도 **응답 어디에도 아이디가 없다** — 값은 메일로만")
    void findId_registeredEmail_neverLeaksIdInResponse() throws Exception {
        String id = "fid_" + UUID.randomUUID().toString().substring(0, 8);
        String email = "zz_" + id + "@example.com";
        mockMvc.perform(post("/api/auth/signup").contentType(JSON)
                        .content(signupBody(id, "ZZ찾기" + id.substring(4), email)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/auth/find-id").header("X-Real-IP", uniqueIp())
                        .contentType(JSON).content(findIdBody(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 여기가 이 기능의 핵심 제약이다 — 아이디가 응답에 실리면 주소를 넣어 보며 계정을 수집할 수 있다.
        // 없는 주소(위 테스트)와 **바이트 단위로 구분되지 않는** 응답이어야 한다.
        assertThat(body).doesNotContain(id);
    }

    @Test
    @DisplayName("아이디 찾기: 같은 주소로 3회 200 → 4회째 429(AUTH-429F)")
    void findId_throttledPerEmail() throws Exception {
        // 주소는 매 실행 새로 만든다 — 주소 카운터가 실행 간에 이어지면 첫 요청부터 429 가 난다.
        String email = "zz_thr_" + UUID.randomUUID() + "@example.com";
        String ip = uniqueIp();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/find-id").header("X-Real-IP", ip)
                            .contentType(JSON).content(findIdBody(email)))
                    .andExpect(status().isOk());
        }
        // ⚠ 가입되지 않은 주소로 태운다 — 없는 주소도 똑같이 잠겨야 429 가 가입 여부를 알려주지 않는다.
        mockMvc.perform(post("/api/auth/find-id").header("X-Real-IP", ip)
                        .contentType(JSON).content(findIdBody(email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("AUTH-429F"));
    }

    @Test
    @DisplayName("아이디 찾기: 이메일 형식이 아니면 400 — 제한 카운터를 태우기 전에 막힌다")
    void findId_invalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/find-id").header("X-Real-IP", uniqueIp())
                        .contentType(JSON).content(findIdBody("not-an-email")))
                .andExpect(status().isBadRequest());
    }
}
