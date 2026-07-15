package com.glassvue.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 공통 응답 래퍼.
 * - 성공: { "success": true, "data": ... }
 * - 에러: { "success": false, "error": { "code": ..., "message": ... } }
 * HTTP 상태코드는 그대로 유지한다(에러도 4xx/5xx).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message));
    }

    public record ErrorBody(String code, String message) {
    }
}
