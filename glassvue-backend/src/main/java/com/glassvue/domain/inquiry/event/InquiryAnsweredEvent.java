package com.glassvue.domain.inquiry.event;

import com.glassvue.global.messaging.DomainEvent;
import java.util.UUID;

/**
 * 상품 문의에 <b>관리자 답변이 처음 달렸을 때</b> 발행되는 도메인 이벤트 (2026-07-31, B-15).
 *
 * <p>발행 주체는 문의를 소유한 <b>inquiry</b> 다. 구독자(알림)는 notification 도메인에 있고,
 * 그래서 inquiry 는 알림의 존재를 모른다 — 재고 이벤트를 catalog 가 내고 notification 이 받는 것과 같다.
 *
 * <p><b>왜 "처음"만인가</b>: 답변은 관리자가 수정할 수 있는데({@code answer()} 는 등록·수정 겸용),
 * 수정할 때마다 알림이 나가면 <b>오타 하나 고칠 때마다 사용자에게 알림이 간다.</b>
 * 발행 조건은 상태 전이(WAITING → ANSWERED)로 잡는다 — "값이 바뀌었나"가 아니라 "처음 답이 달렸나"다.
 *
 * <p>{@code inquiryTitle} 을 실어 보내는 이유: 알림 문구에 <b>어느 문의인지</b>가 있어야 쓸모가 있다.
 * 상품명이 더 친절하겠지만 그러면 notification 이 catalog 를 조회해야 한다 — 문의 제목은 이미
 * inquiry 가 가진 값이라 <b>경계를 넘지 않고</b> 같은 목적을 만족한다.
 */
public record InquiryAnsweredEvent(
        UUID inquiryId,
        UUID productId,
        UUID authorId,
        String inquiryTitle
) implements DomainEvent {
}
