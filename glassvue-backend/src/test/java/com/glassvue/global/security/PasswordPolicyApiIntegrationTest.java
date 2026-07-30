package com.glassvue.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.auth.service.AuthService;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 정책(E-3)이 <b>세 경로 전부</b>에 걸려 있는지 — 가입 · 비밀번호 변경 · 재설정 확정.
 *
 * <p>⚠ 단위 테스트({@link PasswordPolicyTest})가 규칙을 보고, 여기는 <b>배선</b>을 본다. 정책 클래스가
 * 아무리 맞아도 <b>세 경로 중 하나에서 부르는 것을 잊으면</b> 그 문으로 약한 비밀번호가 들어온다 —
 * 규칙과 배선은 다른 실패다(감사 로그에서 "리스너 이후가 검증된 적 없던" 것과 같은 종류).
 *
 * <p>⚠ 기존 회원은 <b>소급 적용되지 않는다</b>는 것도 함께 고정한다: 리포지토리로 직접 만든
 * {@code password123} 계정은 로그인이 계속 되어야 한다(데모 계정을 그대로 둔 결정 — 2026-07-29 §10-4).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordPolicyApiIntegrationTest {

    private static final String JSON_TYPE = "application/json";
    /** 정책을 통과하는 값(10자 이상 · 목록에 없음 · 아이디/닉네임과 무관). */
    private static final String STRONG = "Tulip-Harbor-72";
    /** 예전 하한(8자)은 통과하던 값. 지금은 목록에 있어 거부된다. */
    private static final String LEGACY = "password123";

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthService authService;

    private String loginId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        loginId = "zzpolicy_" + suffix;
        // ⚠ 리포지토리로 직접 만든다 = **정책을 안 탄다**. 기존 회원(데모 계정)을 재현하는 것이다.
        memberId = memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(LEGACY))
                .nickname("ZZ정책" + suffix).role(Role.USER).build()).getId();
    }

    private String signupBody(String id, String nickname, String password) {
        return "{\"loginId\":\"" + id + "\",\"password\":\"" + password
                + "\",\"nickname\":\"" + nickname + "\",\"email\":\"" + id + "@example.com\"}";
    }

    private String login(String id, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON_TYPE)
                        .content("{\"loginId\":\"" + id + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    // ---------- 가입 ----------

    @Test
    @DisplayName("가입: 흔한 비밀번호 → 400(AUTH-400P2) / 정상 값 → 201")
    void signup_rejectsCommonPassword() throws Exception {
        String newId = "zzpol2_" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/auth/signup").contentType(JSON_TYPE)
                        .content(signupBody(newId, "ZZ흔한" + newId.substring(7), LEGACY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH-400P2"));
        // 대조군 — 같은 요청에서 비밀번호만 바꾸면 통과해야 한다(정책이 아닌 다른 이유로 막힌 게 아님)
        mockMvc.perform(post("/api/auth/signup").contentType(JSON_TYPE)
                        .content(signupBody(newId, "ZZ정상" + newId.substring(7), STRONG)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("가입: 9자 → 400(COMMON-400, DTO @Size 가 먼저 걸린다)")
    void signup_rejectsShortPassword() throws Exception {
        String newId = "zzpol3_" + UUID.randomUUID().toString().substring(0, 8);
        // ⚠ 정책 클래스의 TOO_SHORT 가 아니라 **DTO 검증**이 먼저 잡는다. 둘 다 10자로 맞춰 뒀고,
        //    어긋나면 이 테스트가 깨진다(한쪽만 고치는 실수를 잡는 자리다).
        mockMvc.perform(post("/api/auth/signup").contentType(JSON_TYPE)
                        .content(signupBody(newId, "ZZ짧음" + newId.substring(7), "abc123456")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("가입: 아이디를 포함한 비밀번호 → 400(AUTH-400P3)")
    void signup_rejectsPasswordContainingLoginId() throws Exception {
        String newId = "zzpol4_" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/auth/signup").contentType(JSON_TYPE)
                        .content(signupBody(newId, "ZZ포함" + newId.substring(7), newId + "-99")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH-400P3"));
    }

    // ---------- 비밀번호 변경 ----------

    @Test
    @DisplayName("변경: 흔한 비밀번호로는 못 바꾼다 → 400(AUTH-400P2), 정상 값은 200")
    void changePassword_appliesPolicy() throws Exception {
        String token = login(loginId, LEGACY); // ⚠ 기존 계정은 그대로 로그인된다(소급 적용 없음)

        mockMvc.perform(patch("/api/members/me/password").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + LEGACY + "\",\"newPassword\":\"qwertyuiop\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH-400P2"));

        mockMvc.perform(patch("/api/members/me/password").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + LEGACY + "\",\"newPassword\":\"" + STRONG + "\"}"))
                .andExpect(status().isOk());
    }

    // ---------- 재설정 확정 ----------

    @Test
    @DisplayName("재설정 확정: 정책 위반이면 400 — ⚠ 그때 토큰은 이미 소비된다")
    void confirmPasswordReset_appliesPolicy() throws Exception {
        String token = authService.requestPasswordReset(loginId).orElseThrow();

        mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(JSON_TYPE)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + LEGACY + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH-400P2"));

        // ⚠ 같은 토큰을 다시 쓸 수 없다 — 정책 검사보다 **토큰 소비가 먼저**라 그렇다(의도된 순서).
        // 편의를 위해 순서를 뒤집으면 링크의 단발성이 깨진다. 사용자는 재설정을 다시 요청하면 된다.
        mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(JSON_TYPE)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + STRONG + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH-400R"));

        // 새로 받은 토큰으로는 정상 값이 통한다
        String fresh = authService.requestPasswordReset(loginId).orElseThrow();
        mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(JSON_TYPE)
                        .content("{\"token\":\"" + fresh + "\",\"newPassword\":\"" + STRONG + "\"}"))
                .andExpect(status().isOk());
    }
}
