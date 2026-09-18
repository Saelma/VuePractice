package com.glassvue.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 페이지 크기 상한 — 넘으면 <b>자르지 않고 거절한다</b> (2026-09-18, BACKLOG H-3).
 *
 * <p>🔴 <b>대조군이 먼저다</b> — «100 은 통과» 가 없으면 아래 400 들은 «상한» 이 아니라 «그 경로가 원래 400» 일 수 있다(WA §3-3).
 * ⚠ 공개 목록(`/api/products`)으로 본다 — 인증 없이 닿아, 401/403 이 400 을 가리지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PageSizeGuardIntegrationTest {

    @Autowired MockMvc mockMvc;

    private static final int MAX = PageSizeGuard.MAX_PAGE_SIZE;

    @Test
    @DisplayName("🔴 대조군 — 상한과 같은 크기(100)는 통과하고, 그 크기로 답한다")
    void atLimitPasses() throws Exception {
        mockMvc.perform(get("/api/products").param("size", String.valueOf(MAX)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(MAX));
    }

    @Test
    @DisplayName("🔴 상한을 넘으면(101) 400 COMMON-400S — **조용히 100 으로 잘라 200 을 주지 않는다**")
    void overLimitIsRejected() throws Exception {
        mockMvc.perform(get("/api/products").param("size", String.valueOf(MAX + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400S"));
    }

    @Test
    @DisplayName("⚠ int 를 넘는 자릿수도 400 — 파싱 실패로 흘러 «기본 크기» 가 되지 않는다")
    void overflowIsRejected() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "99999999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-400S"));
    }

    @Test
    @DisplayName("🔴 부호·유니코드 숫자도 값으로 본다 — 스프링은 `+500`·`٥٠٠` 을 500 으로 읽는다 (2026-09-18 /code-review)")
    void signedAndUnicodeDigitsAreRejected() throws Exception {
        // ⚠ 처음 판은 `\d+` 로 걸러 이 둘을 «숫자 아님» 으로 통과시켰고, 스프링이 500건을 200 으로 줬다.
        for (String size : new String[]{"+500", "\u0665\u0660\u0660"}) {
            mockMvc.perform(get("/api/products").param("size", size))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON-400S"));
        }
    }

    @Test
    @DisplayName("앞자리 0 은 길이가 아니라 값으로 — `0000000000050` 은 50 이라 통과한다")
    void leadingZerosAreJudgedByValue() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "0000000000050"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50));
    }

    @Test
    @DisplayName("숫자가 아닌 size 는 여기서 판단하지 않는다 — 스프링이 기본 크기로 푼다(늘리는 쪽이 아니다)")
    void nonNumericFallsBackToDefault() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "abc"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("⚠ Pageable 을 안 받는 엔드포인트는 size 가 커도 건드리지 않는다 — 이름이 같다고 같은 뜻이 아니다")
    void nonPageableEndpointIsUntouched() throws Exception {
        // 가입 쿠폰 안내는 목록이 아니다(Pageable 없음). size 는 그냥 무시되는 파라미터여야 한다.
        mockMvc.perform(get("/api/coupons/welcome").param("size", "500"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("에러 문구의 숫자가 상한 상수와 같다 — 한쪽만 바꾸면 «100개까지» 라고 말하며 다른 값으로 막는다")
    void messageMatchesLimit() {
        assertThat(ErrorCode.PAGE_SIZE_TOO_LARGE.getMessage()).contains(MAX + "개");
    }
}
