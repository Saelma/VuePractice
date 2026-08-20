package com.glassvue.domain.audit.entity;

/**
 * 감사 원장의 {@code target_id} 가 <b>무엇의 id 인지</b> (2026-08-20, V53).
 *
 * <p>🔴 <b>이벤트로 받지 않는다</b> — {@link AuditAction} 이 스스로 답한다({@code action.targetType()}).
 * 파라미터로 받으면 «action 은 {@code PRODUCT_UPDATE} 인데 targetType 은 {@code MEMBER}» 인 행을
 * 만들 수 있는데, <b>그런 행은 뜻이 없고 만들 수 있게 두면 언젠가 만들어진다.</b>
 *
 * <p>⚠ {@code AdminActionEvent} 의 주석은 원래도 *"무엇인지는 action 이 말한다"* 였다 —
 * <b>사람이 읽는 문장을 기계가 읽는 값으로 옮긴 것</b>뿐이다.
 *
 * <p>⚠ <b>{@code DISCOUNT} 가 없다.</b> 할인 조작의 대상을 <b>상품</b>으로 잡았기 때문이다 —
 * 할인 id 는 사람에게 의미가 없고, 궁금한 것은 «어느 상품의 세일인가» 다. 대상을 상품으로 잡으면
 * 상품 수정·삭제·할인이 <b>같은 {@code target_id} 로 묶여</b> «이 상품에 무슨 일이 있었나» 를
 * 훑을 수 있다(그건 다른 방법이 없다). V43 이 주문 취소의 대상을 «주문» 이 아니라 «주문자» 로
 * 잡은 것과 같은 결이다.
 */
public enum AuditTargetType {
    /** 대상이 회원. {@code target_login} 이 채워진다(탈퇴했으면 null). */
    MEMBER,
    /** 대상이 상품. 상품 등록·수정·삭제·복구와 <b>기간 할인 조작</b>이 여기다. */
    PRODUCT,
    /** 대상이 쿠폰 <b>정의</b>. 관리자 수동 발급({@code COUPON_ISSUE})은 대상이 «받는 회원» 이라 여기가 아니다. */
    COUPON
}
