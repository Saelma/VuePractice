package com.glassvue.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 전역 예외 처리 통합 테스트(실 컨텍스트 + MockMvc).
 *
 * <p>본문을 받는 API에 <b>본문이 없거나 JSON이 깨졌을 때</b>가 대상이다. 핸들러가 없으면
 * {@code Exception} 핸들러로 떨어져 <b>클라이언트 잘못인데 500</b>이 나간다 —
 * 2026-07-21에 주문 배송지 본문이 생기면서 드러났지만, 원래 <b>본문을 받는 모든 API</b>에
 * 있던 구멍이었다. 그래서 다시 뚫리지 않게 여기서 고정한다.
 *
 * <p>검증 대상이 {@code @RestControllerAdvice}(전역)이므로 엔드포인트는 무엇이든 상관없다.
 * 인증 없이 닿을 수 있는 로그인 API를 쓴다 — 인증 경계에서 401로 먼저 걸리면
 * 정작 확인하려는 핸들러까지 요청이 도달하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerIntegrationTest {

    @Autowired MockMvc mockMvc;

    private static final String JSON = "application/json";

    @Test
    @DisplayName("본문 없는 POST → 500이 아니라 400")
    void missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("깨진 JSON 본문 → 500이 아니라 400")
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(JSON).content("{\"loginId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("파싱 실패 메시지는 내부 구조를 드러내지 않는다")
    void malformedJson_doesNotLeakParserDetail() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(JSON).content("{\"loginId\":"))
                .andExpect(jsonPath("$.error.message").value("요청 본문을 읽을 수 없습니다."));
    }
}
