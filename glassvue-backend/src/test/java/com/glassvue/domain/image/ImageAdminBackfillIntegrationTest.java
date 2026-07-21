package com.glassvue.domain.image;

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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파생본 백필 엔드포인트(`POST /api/admin/images/derivatives`) — 권한(401/403/200).
 *
 * <p>권한 규칙은 서비스 단위 테스트로 절대 안 잡히고 실제 요청을 보내야만 드러난다
 * (2026-07-20 이미지 업로드 ADMIN 사고 계열). 새 관리자 엔드포인트엔 반드시 붙인다.
 *
 * <p>DB_HOST 있을 때만 실행(= .env 소싱). @Transactional 롤백 → 공유 DB 무오염.
 *
 * <p>⚠ <b>주의</b>: {@code admin_ok}는 실제 백필을 돌린다. 파생본이 없는 이미지가 남아 있으면 파일을
 * 만들고, <b>롤백은 DB만 되돌리고 파일은 지우지 않는다</b>(§3). 다만 파일명이 결정적({@code {uuid}_m/_t.webp})이라
 * 이후 실제 백필이 같은 파일을 덮어쓰며 DB가 참조하게 되고, <b>한 번 백필한 뒤에는 대상이 0건</b>이라
 * 이 테스트는 아무 파일도 만들지 않는다. 그래서 무해하지만, 이 성질에 기대고 있다는 걸 알고 있어야 한다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImageAdminBackfillIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String PW = "password123";
    private static final String PATH = "/api/admin/images/derivatives";

    private String adminLoginId;
    private String userLoginId;

    @BeforeEach
    void setUp() {
        adminLoginId = "zzadm_" + UUID.randomUUID().toString().substring(0, 8);
        userLoginId = "zzusr_" + UUID.randomUUID().toString().substring(0, 8);
        member(adminLoginId, "ZZ백필관리자_" + UUID.randomUUID().toString().substring(0, 6), Role.ADMIN);
        member(userLoginId, "ZZ백필사용자_" + UUID.randomUUID().toString().substring(0, 6), Role.USER);
    }

    private void member(String loginId, String nickname, Role role) {
        memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role).build());
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    @Test
    @DisplayName("비로그인 → 401")
    void anonymous_unauthorized() throws Exception {
        mockMvc.perform(post(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 사용자(USER) → 403")
    void user_forbidden() throws Exception {
        mockMvc.perform(post(PATH).header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 → 200, 집계(대상·갱신·건너뜀) 반환")
    void admin_ok() throws Exception {
        mockMvc.perform(post(PATH).header("Authorization", login(adminLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targets").exists())
                .andExpect(jsonPath("$.data.updated").exists())
                .andExpect(jsonPath("$.data.skipped").exists());
    }
}
