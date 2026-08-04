package com.glassvue.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

/**
 * 어떤 예외를 <b>어느 핸들러가 받는지</b>를 고정한다 (2026-08-04).
 *
 * <p>{@code @RestControllerAdvice} 에 {@code Exception} 을 받는 포괄 핸들러가 있으면,
 * 그 뒤에 붙이는 구체 핸들러는 <b>"실제로 먼저 잡히는가" 가 곧 규칙</b>이 된다. 안 잡히면
 * 코드는 그대로 있는데 아무 일도 안 하고, <b>그 사실이 겉으로 안 드러난다</b>(둘 다 200/500 을
 * 내는 게 아니라 로그 차이라서 더 그렇다).
 *
 * <p>통합 테스트로는 이걸 재현하기 어렵다 — 클라이언트가 끊긴 상황을 MockMvc 로 만들 수 없다.
 * 그래서 스프링이 실제로 쓰는 {@link ExceptionHandlerMethodResolver} 에 <b>직접 물어본다.</b>
 */
class GlobalExceptionHandlerResolutionTest {

    private final ExceptionHandlerMethodResolver resolver =
            new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

    @Test
    @DisplayName("끊긴 클라이언트 예외는 포괄 핸들러가 아니라 **전용 핸들러**가 받는다")
    void disconnectedClient_resolvesToDedicatedHandler() {
        Method method = resolver.resolveMethod(
                new AsyncRequestNotUsableException("Servlet container error notification for disconnected client"));

        assertThat(method).isNotNull();
        assertThat(method.getName())
                .as("Exception 핸들러가 먼저 잡으면 ERROR 로그가 남아 '배포 후 ERROR 0건' 확인이 오염된다")
                .isEqualTo("handleDisconnectedClient");
    }

    @Test
    @DisplayName("그 핸들러는 **응답 본문을 만들지 않는다**(void) — 받을 사람이 없는데 쓰려다 2차 실패했다")
    void disconnectedClientHandler_writesNoBody() {
        Method method = resolver.resolveMethod(new AsyncRequestNotUsableException("gone"));

        // ApiResponse 를 돌려주면 text/event-stream 응답에 쓰려다
        // HttpMessageNotWritableException 이 또 난다 — 실제로 그렇게 두 번 실패하고 있었다.
        assertThat(method.getReturnType()).isEqualTo(void.class);
    }

    @Test
    @DisplayName("⚠ 그 밖의 예외는 여전히 포괄 핸들러가 받는다(실패를 숨긴 게 아니다)")
    void otherExceptions_stillResolveToUnexpectedHandler() {
        assertThat(resolver.resolveMethod(new IllegalStateException("boom")).getName())
                .isEqualTo("handleUnexpected");
    }
}
