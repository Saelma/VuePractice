package com.glassvue.domain.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 반품 거절 요청 (2026-08-11, V47) — <b>관리자 전용</b>.
 *
 * <p>⚠ 사유가 <b>필수</b>인 이유는 {@link AdminOrderCancelRequest} 와 같다:
 * <b>고객은 자기가 왜 요청했는지 알지만, 남이 거절한 이유는 사유가 유일한 단서</b>다.
 * 게다가 거절은 상태를 남기지 않아({@code DELIVERED} 로 되돌아간다) 사유가 없으면
 * <b>거절이 있었다는 사실조차 화면에서 사라진다</b> — 실제로 그랬다(오늘 사용자 지적).
 *
 * <p>⚠ 승인({@code approveReturn})에는 요청 본문이 없다. 승인은 «고객이 요청한 대로 해 준다» 라
 * 설명할 것이 없지만, 거절은 <b>고객의 요청을 뒤집는 결정</b>이라 근거가 따라와야 한다.
 *
 * @param reason 자유 입력 사유. ⚠ {@code max} 는 {@code orders.return_rejected_reason
 *               VARCHAR2(500 CHAR)} 와 <b>같아야</b> 한다 — 더 헐거우면 검증을 통과한 값이
 *               INSERT 에서 ORA-12899 로 거절 자체를 실패시킨다(취소 사유와 같은 이유·같은 값).
 */
public record ReturnRejectRequest(
        @NotBlank(message = "거절 사유를 입력해 주세요.")
        @Size(max = 500, message = "거절 사유는 500자를 넘을 수 없습니다.")
        String reason
) {
}
