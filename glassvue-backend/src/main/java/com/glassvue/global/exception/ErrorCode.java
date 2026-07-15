package com.glassvue.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 카탈로그. 코드 · HTTP 상태 · 기본 메시지를 한곳에 모은다.
 * 도메인에서는 throw new BusinessException(ErrorCode.XXX) 로만 사용한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT("COMMON-400", HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR("COMMON-500", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 공지(notice)
    NOTICE_NOT_FOUND("NOTICE-404", HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
