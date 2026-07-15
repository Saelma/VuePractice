package com.glassvue.global.exception;

import lombok.Getter;

/**
 * 업무 예외. 항상 ErrorCode를 지닌다.
 * 전역 핸들러(GlobalExceptionHandler)가 받아 ApiResponse 에러로 변환한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 메시지 대신 상황별 메시지를 실을 때 (코드는 그대로). */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
