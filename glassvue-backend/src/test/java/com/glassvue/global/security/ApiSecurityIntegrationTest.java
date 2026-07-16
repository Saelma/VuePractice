package com.glassvue.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SecurityConfig 라우팅 규칙 통합 테스트(실 컨텍스트 + MockMvc). 토큰 없이 접근 시의 공개/인증 경계를 검증.
 * DB_HOST 있을 때만 실행(= .env 소싱).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("공개: 상품 목록 조회는 비로그인 200")
    void products_public() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("관리자 전용: 비로그인 상품 등록 → 401(인증 필요)")
    void productCreate_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/products").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("인증 필요: 비로그인 주문 목록 → 401")
    void orders_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자 전용: 비로그인 발송 처리 → 401")
    void ship_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/orders/" + UUID.randomUUID() + "/ship"))
                .andExpect(status().isUnauthorized());
    }
}
