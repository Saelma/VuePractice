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
    // 아래 둘은 **도메인이 던지지 않는다** — 스프링이 던진 예외를 전역 핸들러가 옮겨 담는 자리다.
    // 도메인별 404(PRODUCT-404 등)는 "그 자원이 없다"이고, 이건 "그런 경로/메서드가 없다"라 층이 다르다.
    ENDPOINT_NOT_FOUND("COMMON-404", HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED("COMMON-405", HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 메서드입니다."),
    INTERNAL_ERROR("COMMON-500", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 공지(notice)
    NOTICE_NOT_FOUND("NOTICE-404", HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다."),
    NOTICE_NOT_OWNER("NOTICE-403", HttpStatus.FORBIDDEN, "본인 글만 수정·삭제할 수 있습니다."),

    // 회원 · 인증
    MEMBER_NOT_FOUND("MEMBER-404", HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    DUPLICATE_LOGIN_ID("AUTH-409", HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    DUPLICATE_NICKNAME("MEMBER-409", HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    DUPLICATE_EMAIL("MEMBER-409E", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    EMAIL_REQUIRED("MEMBER-400E", HttpStatus.BAD_REQUEST, "이메일을 먼저 등록해 주세요."),
    EMAIL_ALREADY_VERIFIED("MEMBER-409V", HttpStatus.CONFLICT, "이미 인증된 이메일입니다."),
    // ⚠ 만료·횟수초과·불일치를 구분하지 않는다 — 구분해 주면 남은 시도 횟수를 세어 볼 수 있다.
    INVALID_VERIFICATION_CODE("MEMBER-400V", HttpStatus.BAD_REQUEST, "인증번호가 올바르지 않거나 만료되었습니다."),
    LOGIN_FAILED("AUTH-401", HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    /**
     * 로그인 시도 과다 (E-1, 2026-07-30). ⚠ 실패 카운트를 <b>DB 조회 전, 입력된 아이디 그대로</b> 세므로
     * 없는 아이디도 똑같이 잠긴다 — 그래서 이 응답이 <b>계정 존재를 알려주지 않는다</b>(열거 방지 유지).
     */
    TOO_MANY_LOGIN_ATTEMPTS("AUTH-429", HttpStatus.TOO_MANY_REQUESTS,
            "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    /**
     * 비밀번호 정책 위반 (E-3, 2026-07-30). 셋을 <b>구분해서</b> 답한다 — 로그인 실패와 달리
     * 여기는 <b>본인이 값을 새로 정하는 자리</b>라, 이유를 알려주지 않으면 사용자가 고칠 수 없다
     * (열거 위험도 없다: 대상이 이미 자기 계정이다).
     */
    WEAK_PASSWORD_TOO_SHORT("AUTH-400P1", HttpStatus.BAD_REQUEST, "비밀번호는 10자 이상이어야 합니다."),
    WEAK_PASSWORD_COMMON("AUTH-400P2", HttpStatus.BAD_REQUEST,
            "너무 흔한 비밀번호입니다. 다른 값을 사용해 주세요."),
    WEAK_PASSWORD_CONTAINS_ID("AUTH-400P3", HttpStatus.BAD_REQUEST,
            "비밀번호에 아이디나 닉네임을 포함할 수 없습니다."),
    /**
     * 필수 동의 누락 (2026-08-03, B-21). ⚠ 빈 검증(`COMMON-400`)이 아니라 <b>전용 코드</b>인 이유:
     * 동의 누락은 <b>형식 오류가 아니라 정책</b>이고, 프론트가 코드로 <b>어느 체크박스를 붉힐지</b>
     * 고를 수 있어야 한다(비밀번호 정책을 DTO 가 아니라 서비스에 둔 것과 같은 판단).
     */
    TERMS_NOT_AGREED("AUTH-400T", HttpStatus.BAD_REQUEST,
            "이용약관과 개인정보 처리방침에 동의해야 가입할 수 있습니다."),
    /**
     * 재설정 요청 과다 (2026-07-30). ⚠ 이 경로는 <b>열거 방지가 존재 이유</b>인 자리다 — 그래서
     * 카운트를 DB 조회 전, 입력된 아이디 그대로 해서 <b>없는 아이디도 똑같이 잠기게</b> 한다
     * (그러지 않으면 429 가 "그 계정은 있다" 는 신호가 되어 제한을 붙이면서 목적을 깨뜨린다).
     */
    TOO_MANY_RESET_REQUESTS("AUTH-429R", HttpStatus.TOO_MANY_REQUESTS,
            "재설정 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    /**
     * 아이디 찾기 요청 과다 (2026-07-31, G-1). 위와 같은 이유·같은 규칙이다 —
     * 세는 대상만 아이디가 아니라 <b>이메일</b>이다({@code FindLoginIdGuard}).
     */
    TOO_MANY_FIND_ID_REQUESTS("AUTH-429F", HttpStatus.TOO_MANY_REQUESTS,
            "아이디 찾기 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    UNAUTHENTICATED("AUTH-401U", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN("AUTH-401T", HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다."),
    PASSWORD_MISMATCH("AUTH-400P", HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    // 비밀번호 재설정 토큰이 없거나 만료·이미 사용됨(단발성). 토큰 유무를 자세히 알려주지 않는다.
    INVALID_RESET_TOKEN("AUTH-400R", HttpStatus.BAD_REQUEST, "재설정 링크가 유효하지 않거나 만료되었습니다."),
    ACCESS_DENIED("AUTH-403", HttpStatus.FORBIDDEN, "권한이 없습니다."),

    // 회원 정지 · 관리자 조작 (2026-07-28, B-11 후속)
    ACCOUNT_SUSPENDED("AUTH-403S", HttpStatus.FORBIDDEN, "정지된 계정입니다. 관리자에게 문의하세요."),
    CANNOT_MODIFY_SELF("MEMBER-400S", HttpStatus.BAD_REQUEST, "자기 계정은 정지·역할변경할 수 없습니다."),
    // 최상위 관리자(2026-07-28) — 관리자 조작은 SUPER_ADMIN 전용, SUPER_ADMIN 계정은 아무도 못 건드린다.
    SUPER_ADMIN_ONLY("MEMBER-403A", HttpStatus.FORBIDDEN, "관리자 계정의 정지·역할변경은 최상위 관리자만 할 수 있습니다."),
    CANNOT_MODIFY_SUPER_ADMIN("MEMBER-403S", HttpStatus.FORBIDDEN, "최상위 관리자 계정은 정지·변경할 수 없습니다."),
    CANNOT_GRANT_SUPER_ADMIN("MEMBER-400A", HttpStatus.BAD_REQUEST, "최상위 관리자 권한은 이 API로 부여할 수 없습니다."),

    // 배송지 주소록 (2026-07-24)
    // 남의 주소는 403이 아니라 404로 답한다 — 존재 여부 자체를 알려주지 않는다(쿠폰과 같은 판단).
    ADDRESS_NOT_FOUND("ADDRESS-404", HttpStatus.NOT_FOUND, "배송지를 찾을 수 없습니다."),
    ADDRESS_LIMIT_EXCEEDED("ADDRESS-409", HttpStatus.CONFLICT, "배송지는 최대 10개까지 저장할 수 있습니다."),

    // 카탈로그(상품·카테고리)
    CATEGORY_NOT_FOUND("CATEGORY-404", HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    DUPLICATE_CATEGORY("CATEGORY-409", HttpStatus.CONFLICT, "이미 있는 카테고리입니다."),
    CATEGORY_IN_USE("CATEGORY-409U", HttpStatus.CONFLICT, "소속 상품이 있어 삭제할 수 없습니다."),
    PRODUCT_NOT_FOUND("PRODUCT-404", HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    OUT_OF_STOCK("PRODUCT-400S", HttpStatus.BAD_REQUEST, "재고가 부족합니다."),
    PRODUCT_NO_VARIANT("PRODUCT-400V", HttpStatus.BAD_REQUEST, "상품은 옵션이 최소 1개 있어야 합니다."),
    VARIANT_NOT_FOUND("PRODUCT-404V", HttpStatus.NOT_FOUND, "상품 옵션을 찾을 수 없습니다."),
    // ⚠ 이 규칙은 2026-08-13 까지 **화면에만** 있었다 — API 로 부르면 정가 0원·정가 < 판매가가
    //    그대로 저장됐고, 그러면 상세 화면이 뜻 없는 취소선을 그린다(할인이 아닌데 할인처럼 보인다).
    PRODUCT_LIST_PRICE_NOT_HIGHER("PRODUCT-400L", HttpStatus.BAD_REQUEST,
            "정가는 판매가보다 커야 합니다. 할인이 없으면 정가를 비워 두세요."),

    // 주문
    CART_EMPTY("ORDER-400E", HttpStatus.BAD_REQUEST, "장바구니가 비어 있습니다."),
    UNAVAILABLE_ITEM("ORDER-400U", HttpStatus.BAD_REQUEST, "구매할 수 없는 상품(품절·판매중지)이 포함되어 있습니다."),
    ORDER_NOT_FOUND("ORDER-404", HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_NOT_CANCELLABLE("ORDER-400C", HttpStatus.BAD_REQUEST, "취소할 수 없는 주문입니다(발송 완료·취소된 주문)."),
    ORDER_NOT_PAYABLE("ORDER-400P", HttpStatus.BAD_REQUEST, "결제할 수 없는 주문입니다(이미 결제·취소됨)."),
    ORDER_NOT_SHIPPABLE("ORDER-400H", HttpStatus.BAD_REQUEST, "발송할 수 없는 주문입니다(결제 완료 상태만 발송 가능)."),
    ORDER_NOT_DELIVERABLE("ORDER-400D", HttpStatus.BAD_REQUEST, "배송완료 처리할 수 없는 주문입니다(발송된 주문만 가능)."),
    ORDER_NOT_RETURNABLE("ORDER-400R", HttpStatus.BAD_REQUEST, "반품할 수 없는 주문입니다(배송완료된 주문만 요청 가능)."),
    ORDER_NOT_RETURN_PENDING("ORDER-400RP", HttpStatus.BAD_REQUEST, "반품 요청 상태가 아닙니다."),

    // 리뷰
    REVIEW_NOT_FOUND("REVIEW-404", HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."),
    REVIEW_NOT_OWNER("REVIEW-403", HttpStatus.FORBIDDEN, "본인 리뷰만 수정·삭제할 수 있습니다."),
    REVIEW_NOT_PURCHASED("REVIEW-403P", HttpStatus.FORBIDDEN, "구매한 상품만 리뷰할 수 있습니다."),
    DUPLICATE_REVIEW("REVIEW-409", HttpStatus.CONFLICT, "이미 이 상품에 리뷰를 작성했습니다."),

    // 문의
    INQUIRY_NOT_FOUND("INQUIRY-404", HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다."),
    INQUIRY_NOT_OWNER("INQUIRY-403", HttpStatus.FORBIDDEN, "본인 문의만 수정·삭제할 수 있습니다."),
    INQUIRY_ALREADY_ANSWERED("INQUIRY-400A", HttpStatus.BAD_REQUEST, "답변이 완료된 문의는 수정할 수 없습니다."),
    INQUIRY_SELF_ANSWER("INQUIRY-403S", HttpStatus.FORBIDDEN, "본인이 등록한 문의에는 답변할 수 없습니다."),
    // 일반 문의(G-3 2단계)에 PRODUCT 유형을 보낸 경우. 상품 문의는 상품 경로로만 만든다 —
    // 여기서 막지 않으면 productId 없는 PRODUCT 행이 생겨 관리자 목록의 상품명이 영원히 «—» 가 된다.
    INQUIRY_TYPE_NOT_GENERAL("INQUIRY-400T", HttpStatus.BAD_REQUEST,
            "상품 문의는 상품 페이지에서 등록해 주세요."),

    COUPON_NOT_FOUND("COUPON-404", HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없습니다."),
    COUPON_ALREADY_USED("COUPON-409", HttpStatus.CONFLICT, "이미 사용한 쿠폰입니다."),
    COUPON_EXPIRED("COUPON-400E", HttpStatus.BAD_REQUEST, "사용 기간이 아닌 쿠폰입니다."),
    COUPON_MIN_ORDER_NOT_MET("COUPON-400M", HttpStatus.BAD_REQUEST, "쿠폰의 최소 주문금액을 채우지 못했습니다."),

    // 이벤트 쿠폰 (G-8, V49 — 2026-08-13)
    // ⚠ 「이미 받음」은 실패지만 **되돌릴 것이 없는 실패**다 — 화면은 이 코드를 보고 버튼을
    //    「받음」으로 확정한다(에러 토스트가 아니라 상태 확정이다).
    COUPON_ALREADY_ISSUED("COUPON-409I", HttpStatus.CONFLICT, "이미 받은 쿠폰입니다."),
    COUPON_EVENT_CLOSED("COUPON-400C", HttpStatus.BAD_REQUEST, "지금은 받을 수 있는 이벤트 쿠폰이 없습니다."),
    // 등록 시 검증 둘. 앱이 유일한 방어라(Oracle 유니크로는 기간 겹침을 못 막는다) 테스트로 못 박는다.
    COUPON_EVENT_OVERLAP("COUPON-400O", HttpStatus.BAD_REQUEST,
            "발급 기간이 겹치는 이벤트 쿠폰이 이미 있습니다."),
    COUPON_ISSUE_WINDOW_INVALID("COUPON-400W", HttpStatus.BAD_REQUEST,
            "발급 마감은 시작일 이후, 사용 마감 이전이어야 합니다."),

    // 적립금 · 회원 등급 (2026-07-24)
    POINT_NOT_ENOUGH("POINT-400N", HttpStatus.BAD_REQUEST, "적립금이 부족합니다."),
    POINT_INVALID_AMOUNT("POINT-400A", HttpStatus.BAD_REQUEST, "적립금 사용액이 올바르지 않습니다."),
    POINT_EXCEEDS_ORDER("POINT-400E", HttpStatus.BAD_REQUEST, "적립금은 상품 금액을 넘을 수 없습니다."),

    NOTIFICATION_NOT_FOUND("NOTIFICATION-404", HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

    // 관리자 통계 기간 (B-26, 2026-08-13)
    STATS_PERIOD_INVALID("STATS-400P", HttpStatus.BAD_REQUEST, "시작일이 종료일보다 늦습니다."),
    // ⚠ 상한을 두는 이유는 성능이 아니라 **읽을 수 있는가**다 — 일별 추이는 빈 날을 채워서 주므로
    //    2년을 고르면 막대 730개가 와서 차트가 띠가 된다. 조용히 자르지 않고 거절한다.
    STATS_PERIOD_TOO_LONG("STATS-400L", HttpStatus.BAD_REQUEST, "조회 기간이 너무 깁니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
