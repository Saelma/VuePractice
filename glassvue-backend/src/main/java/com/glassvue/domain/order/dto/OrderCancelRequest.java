package com.glassvue.domain.order.dto;

import jakarta.validation.constraints.Size;

/**
 * 주문 취소 요청 (2026-08-04, 백로그 B-17).
 *
 * <p>⚠ <b>본문 전체가 선택</b>이다 — 사유 없이 취소할 수 있어야 하므로 컨트롤러가
 * {@code @RequestBody(required = false)} 로 받는다. 사유를 강제하면 취소에 마찰이 생기는데,
 * 취소는 고객이 빨리 끝내고 싶은 조작이라 그 마찰이 값보다 크다(반품은 돈이 얽혀 필수다).
 *
 * @param reason 자유 입력 사유. 비워도 된다 — 공백만 오면 엔티티가 NULL 로 눕힌다.
 *               ⚠ {@code max} 는 {@code orders.cancel_reason VARCHAR2(500 CHAR)} 와 <b>같아야</b> 한다.
 *               더 헐거우면 검증을 통과한 값이 INSERT 에서 <b>ORA-12899 로 취소 자체를 실패</b>시킨다
 *               (B-20 에서 배송 요청사항으로 같은 자리를 한 번 짚었다).
 */
public record OrderCancelRequest(
        @Size(max = 500, message = "취소 사유는 500자를 넘을 수 없습니다.")
        String reason
) {
    /** 본문이 아예 없을 수 있다 — 그때도 호출부가 null 검사를 하지 않게 여기서 흡수한다. */
    public static String reasonOf(OrderCancelRequest request) {
        return request == null ? null : request.reason();
    }
}
