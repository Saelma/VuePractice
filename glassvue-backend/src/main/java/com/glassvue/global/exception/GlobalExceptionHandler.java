package com.glassvue.global.exception;

import com.glassvue.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.error(ec.getCode(), ec.getMessage()));
    }
}
