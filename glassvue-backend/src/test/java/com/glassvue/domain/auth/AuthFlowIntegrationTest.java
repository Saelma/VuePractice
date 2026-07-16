package com.glassvue.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired MockMvc mockMvc;

    private static final String JSON = "application/json";
    private final String loginId = "it_" + UUID.randomUUID().toString().substring(0, 8);

    @Test
    @DisplayName("회원가입 → 로그인 → 토큰으로 공지 작성 → 조회")
    void fullFlow() throws Exception {
        // 1) 회원가입
        mockMvc.perform(post("/api/auth/signup").contentType(JSON).content(
                        "{\"loginId\":\"" + loginId + "\",\"password\":\"password123\",\"nickname\":\"통합테스터\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nickname").value("통합테스터"));

        // 2) 로그인 → accessToken 추출
        String loginBody = mockMvc.perform(post("/api/auth/login").contentType(JSON).content(
                        "{\"loginId\":\"" + loginId + "\",\"password\":\"password123\"}"))
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
}
