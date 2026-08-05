package com.glassvue.global.exception;

import com.glassvue.global.response.ApiResponse;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리. 컨트롤러/서비스에서 try-catch로 응답을 만들지 않고 여기서 일괄 변환한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        log.warn("BusinessException: {} - {}", ec.getCode(), e.getMessage());
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.error(ec.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError != null)
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : ec.getMessage();
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.error(ec.getCode(), message));
    }

    /** 경로/쿼리 파라미터 타입 변환 실패 (예: 잘못된 형식의 UUID) → 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        String message = e.getName() + ": 형식이 올바르지 않습니다.";
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.error(ec.getCode(), message));
    }

    /**
     * 요청 본문이 없거나 JSON이 깨졌을 때 → 400.
     * 없으면 `Exception` 핸들러로 떨어져 **클라이언트 잘못인데 500**이 나간다
     * (2026-07-21: 주문에 배송지 본문이 생기면서 드러났다 — 원래 모든 본문 API에 있던 구멍).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        log.warn("Unreadable request body: {}", e.getMessage());
        // 파싱 실패 메시지는 내부 구조를 드러내므로 그대로 내보내지 않는다.
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.error(ec.getCode(), "요청 본문을 읽을 수 없습니다."));
    }

    /**
     * <b>클라이언트가 이미 끊긴 뒤</b>의 실패는 예외 처리 대상이 아니다 (2026-08-04).
     *
     * <p>알림 SSE({@code NotificationStream})는 오래 열려 있는 응답이라, 브라우저가 페이지를 옮기거나
     * 서버가 재시작하면 연결이 끊긴다. 그때 톰캣이 {@link AsyncRequestNotUsableException}
     * ({@code Caused by: Broken pipe})를 올리는데, 아래 {@code Exception} 핸들러가 이걸 받아
     * <b>두 가지를 한다 — 둘 다 쓸모없다</b>:
     * <ol>
     *   <li>{@code ERROR "Unhandled exception"} 을 스택트레이스와 함께 남긴다.
     *       <b>이게 진짜 문제다</b> — 배포 확인에서 *"ERROR 0건"* 을 근거로 쓰는데, 브라우저를 몇 번
     *       움직이기만 해도 오염돼 <b>그 확인이 의미를 잃는다.</b></li>
     *   <li>{@code ApiResponse} 를 응답에 쓰려다 <b>2차로 실패</b>한다
     *       ({@code No converter for ApiResponse with preset Content-Type 'text/event-stream'}) —
     *       애초에 <b>받을 사람이 없는데</b> 답을 쓰려 해서다.</li>
     * </ol>
     *
     * <p>그래서 여기서 먼저 잡아 <b>조용히 흘린다</b>. 응답 본문을 만들지 않으므로({@code void})
     * 2차 실패도 사라진다. 로그는 {@code debug} 로만 남긴다 — 진짜로 궁금할 때만 켜서 본다.
     *
     * <p>⚠ 이건 <b>실패를 숨기는 것이 아니다</b>. 우리가 손쓸 수 있는 일이 없고(상대가 이미 갔다),
     * 남겨 봐야 <b>다른 ERROR 를 가리는</b> 잡음이라 지운다. 서버가 처리 중 죽는 예외는 그대로
     * 아래 핸들러가 받는다.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleDisconnectedClient(AsyncRequestNotUsableException e) {
        log.debug("Client disconnected before the response was written: {}", e.getMessage());
    }

    /**
     * <b>그런 경로가 없다</b> → 404 (2026-08-05).
     *
     * <p>매핑이 하나도 안 맞으면 스프링이 {@link NoResourceFoundException} 을 던지는데, 아래
     * {@code Exception} 핸들러가 받아 <b>500 + {@code ERROR "Unhandled exception"}</b> 을 만들고 있었다.
     * 클라이언트 오타 하나가 서버 오류로 보이고, 어제({@code 2026-08-04}) SSE 건과 <b>같은 이유로</b>
     * 배포 확인의 <i>"ERROR 0건"</i> 을 오염시킨다.
     *
     * <p>⚠ 이 구멍은 <b>경로 모양에 따라 갈려서</b> 잘 안 보였다 — {@code /api/products/오타} 는
     * {@code {id}} 패턴에 걸려 타입 변환 실패(400)로 잘 나가는데, {@code /api/zzz} 처럼
     * <b>어느 패턴에도 안 걸리는</b> 것만 500 이 됐다.
     *
     * <p>백엔드는 정적 리소스를 서빙하지 않는다(nginx 가 프론트를 맡고 {@code /api/} 만 프록시한다) —
     * 즉 여기 오는 것은 <b>전부 API 오타</b>다. 로그는 {@code warn} 으로 남긴다: 클라이언트 잘못이라
     * {@code ERROR} 는 아니지만, <b>프론트가 없는 경로를 부르고 있으면 알아야</b> 한다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        ErrorCode ec = ErrorCode.ENDPOINT_NOT_FOUND;
        log.warn("No endpoint: {} {}", e.getHttpMethod(), e.getResourcePath());
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.error(ec.getCode(), ec.getMessage()));
    }

    /**
     * <b>경로는 있는데 메서드가 다르다</b> → 405 (2026-08-05).
     *
     * <p>위와 같은 구멍의 다른 얼굴이다({@code DELETE /api/notices} 가 500 이었다). 404 와 갈라 두는
     * 이유는 <b>답이 다르기 때문</b>이다 — 404 는 *"주소를 다시 봐라"*, 405 는 *"주소는 맞고 메서드가
     * 틀렸다"* 라, 합치면 프론트가 원인을 못 좁힌다.
     *
     * <p>{@code Allow} 헤더를 함께 준다(HTTP 규약). 무엇이 되는지 알려주는 게 405 의 값이다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        ErrorCode ec = ErrorCode.METHOD_NOT_ALLOWED;
        log.warn("Method not allowed: {} (supported: {})", e.getMethod(), e.getSupportedHttpMethods());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ec.getStatus());
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            builder.allow(supported.toArray(new HttpMethod[0]));
        }
        return builder.body(ApiResponse.error(ec.getCode(), ec.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.error(ec.getCode(), ec.getMessage()));
    }
}
