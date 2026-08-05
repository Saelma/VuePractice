package com.glassvue.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    /**
     * 아래 셋은 2026-08-05에 실측으로 드러난 구멍이다 — <b>매핑이 하나도 안 맞으면 500</b>이었다.
     * 어제 고친 SSE 건과 같은 계열이라 함께 막는다(포괄 {@code Exception} 핸들러가 받으면 안 되는 것).
     */
    @Test
    @DisplayName("없는 경로 → 500이 아니라 404")
    void unknownPath_returns404() throws Exception {
        mockMvc.perform(get("/api/zzz-no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-404"));
    }

    @Test
    @DisplayName("경로는 있고 메서드가 다르면 → 405, 그리고 Allow 로 무엇이 되는지 알려준다")
    void wrongMethod_returns405WithAllow() throws Exception {
        // /api/auth/login 은 POST 전용이다.
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("COMMON-405"))
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")));
    }

    /**
     * ⚠ <b>구멍이 어디였는지도 함께 고정한다.</b> {@code {id}} 패턴에 걸리는 오타는 원래부터
     * 타입 변환 실패(400)로 잘 나갔다 — 이게 404로 바뀌면 "형식이 틀렸다"와 "그런 경로가 없다"가
     * 뭉개져 프론트가 원인을 못 좁힌다.
     */
    @Test
    @DisplayName("{id} 패턴에 걸리는 오타는 여전히 400이다(404로 뭉개지 않는다)")
    void malformedPathVariable_stillReturns400() throws Exception {
        mockMvc.perform(get("/api/notices/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-400"));
    }
}
