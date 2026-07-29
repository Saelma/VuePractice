package com.glassvue.domain.member;

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
 * 이메일 등록·변경 (B-13, 2026-07-29) — {@code PATCH /api/members/me/email}.
 *
 * <p>이 화면이 <b>기존 회원의 유일한 수집 경로</b>다(가입 폼은 신규만 커버하고, 기존 회원은 전원 NULL이라
 * 백필할 출처가 없다). 그래서 여기서 고정하는 계약이 셋이다:
 * <ul>
 *   <li><b>401</b> — 남의 계정 이메일을 바꿀 수 없어야 한다. {@code /api/members/**} 는 인증이 필요하다.</li>
 *   <li><b>정규화</b> — 저장된 값이 소문자여야 유니크 제약이 실제로 중복을 막는다(단위테스트는 리포지토리를
 *       목으로 대체하므로 <b>진짜 DB 제약</b>은 여기서만 드러난다).</li>
 *   <li><b>미등록 표현</b> — 기존 회원의 {@code email} 은 null 로 나와야 화면이 "등록 안 함"을 구분한다.</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberEmailFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final String URL = "/api/members/me/email";

    private String meLoginId;
    private String otherLoginId;
    private UUID meId;

    @BeforeEach
    void setUp() {
        meLoginId = "eml_" + UUID.randomUUID().toString().substring(0, 8);
        otherLoginId = "emlo_" + UUID.randomUUID().toString().substring(0, 8);
        // ⚠ 이메일 없이 만든다 — B-13 이전 가입자(전원 NULL)를 재현하는 것이 이 테스트의 전제다.
        meId = member(meLoginId, "ZZ이메일유저" + meLoginId.substring(4));
        member(otherLoginId, "ZZ이메일타인" + otherLoginId.substring(5));
    }

    private UUID member(String loginId, String nickname) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(Role.USER).build()).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private String body(String email) {
        return "{\"email\":\"" + email + "\"}";
    }

    @Test
    @DisplayName("비로그인 → 401 (남의 계정 이메일을 바꿀 수 없다)")
    void unauthenticated() throws Exception {
        mockMvc.perform(patch(URL).contentType(JSON).content(body("x@example.com")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("기존 회원은 email 이 null 로 나오고, 등록하면 소문자로 저장돼 /me 에 실린다")
    void registerAndSeeInMe() throws Exception {
        String token = login(meLoginId);

        // 등록 전 — "아직 등록 안 함"을 화면이 구분할 수 있어야 한다
        mockMvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").doesNotExist());

        String mixed = "ZZ_" + meLoginId.toUpperCase() + "@Example.COM";
        mockMvc.perform(patch(URL).header("Authorization", token).contentType(JSON).content(body(mixed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(mixed.toLowerCase()));

        // /me 에도 실린다(화면이 설정 폼 초기값으로 쓴다)
        mockMvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(mixed.toLowerCase()));

        // DB 실측 — 응답만 소문자이고 저장은 원본인 경우를 배제한다
        assertThat(memberRepository.findById(meId).orElseThrow().getEmail())
                .isEqualTo(mixed.toLowerCase());
    }

    @Test
    @DisplayName("남이 쓰는 주소(대소문자만 다름) → 409 MEMBER-409E")
    void duplicateAcrossMembers() throws Exception {
        String shared = "zz_" + meLoginId + "@example.com";
        mockMvc.perform(patch(URL).header("Authorization", login(otherLoginId))
                        .contentType(JSON).content(body(shared)))
                .andExpect(status().isOk());

        // ⚠ 대문자로 보내도 막혀야 한다 — 정규화가 없으면 Oracle UNIQUE 가 다른 값으로 보고 통과시킨다
        mockMvc.perform(patch(URL).header("Authorization", login(meLoginId))
                        .contentType(JSON).content(body(shared.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEMBER-409E"));
    }

    @Test
    @DisplayName("형식 오류·빈 값 → 400 (빈 문자열로 지우는 경로는 두지 않는다)")
    void validation() throws Exception {
        String token = login(meLoginId);
        mockMvc.perform(patch(URL).header("Authorization", token).contentType(JSON).content(body("not-an-email")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(URL).header("Authorization", token).contentType(JSON).content(body("")))
                .andExpect(status().isBadRequest());
    }
}
