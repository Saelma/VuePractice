package com.glassvue.domain.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 관리자 대행 주문 취소 요청 (2026-08-10, 백로그 B-25).
 *
 * <p>⚠ <b>사유가 필수라는 점만</b> {@link OrderCancelRequest} 와 다르다. 본인 취소는 사유를 강제하면
 * 마찰이 값보다 커서 선택으로 뒀는데, 여기는 반대다 — <b>고객은 자기가 왜 취소했는지 알지만,
 * 남이 취소한 주문은 사유가 유일한 단서</b>다. 발송 처리가 운송장을 필수로 받는 것과 같은 판단이다
 * (나중에 채워 넣을 경로가 없는 값은 그 자리에서 받는다).
 *
 * <p>⚠ 두 DTO 를 하나로 합치지 않았다. {@code @NotBlank} 를 켜고 끄는 플래그를 두면 <b>검증이
 * 런타임 조건에 달리게</b> 되고, 그러면 «어느 경로에서 필수인가» 가 DTO 를 봐서는 안 보인다.
 *
 * @param reason 자유 입력 사유. ⚠ {@code max} 는 {@code orders.cancel_reason VARCHAR2(500 CHAR)} 와
 *               <b>같아야</b> 한다 — 더 헐거우면 검증을 통과한 값이 INSERT 에서 ORA-12899 로
 *               취소 자체를 실패시킨다({@link OrderCancelRequest} 와 같은 이유로 같은 값이다).
 */
public record AdminOrderCancelRequest(
        @NotBlank(message = "취소 사유를 입력해 주세요.")
        @Size(max = 500, message = "취소 사유는 500자를 넘을 수 없습니다.")
        String reason
) {
}
