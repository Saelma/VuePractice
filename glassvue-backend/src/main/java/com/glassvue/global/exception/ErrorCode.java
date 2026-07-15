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
    NOTICE_NOT_FOUND("NOTICE-404", HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다."),
    NOTICE_NOT_OWNER("NOTICE-403", HttpStatus.FORBIDDEN, "본인 글만 수정·삭제할 수 있습니다."),

    // 회원 · 인증
    MEMBER_NOT_FOUND("MEMBER-404", HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    DUPLICATE_LOGIN_ID("AUTH-409", HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    LOGIN_FAILED("AUTH-401", HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHENTICATED("AUTH-401U", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN("AUTH-401T", HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다."),
    PASSWORD_MISMATCH("AUTH-400P", HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    ACCESS_DENIED("AUTH-403", HttpStatus.FORBIDDEN, "권한이 없습니다."),

    // 카탈로그(상품·카테고리)
    CATEGORY_NOT_FOUND("CATEGORY-404", HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    DUPLICATE_CATEGORY("CATEGORY-409", HttpStatus.CONFLICT, "이미 있는 카테고리입니다."),
    PRODUCT_NOT_FOUND("PRODUCT-404", HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    OUT_OF_STOCK("PRODUCT-400S", HttpStatus.BAD_REQUEST, "재고가 부족합니다."),

    // 주문
    CART_EMPTY("ORDER-400E", HttpStatus.BAD_REQUEST, "장바구니가 비어 있습니다."),
    UNAVAILABLE_ITEM("ORDER-400U", HttpStatus.BAD_REQUEST, "구매할 수 없는 상품(품절·판매중지)이 포함되어 있습니다."),
    ORDER_NOT_FOUND("ORDER-404", HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_NOT_CANCELLABLE("ORDER-400C", HttpStatus.BAD_REQUEST, "취소할 수 없는 주문입니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
