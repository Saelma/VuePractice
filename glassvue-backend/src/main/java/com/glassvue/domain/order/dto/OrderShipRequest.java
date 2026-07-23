package com.glassvue.domain.order.dto;

import com.glassvue.domain.order.entity.DeliveryCarrier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 발송 처리 요청 — 운송장.
 *
 * <p>둘 다 <b>필수</b>다. 운송장 없이 발송 상태로 넘어가면 고객이 추적할 수 없고, 나중에 채워 넣을
 * 경로도 없어 그 주문은 영영 "보냈다"는 사실만 남는다. 그래서 "발송 처리 = 운송장 등록"으로 묶는다.
 *
 * <p>{@code carrier}가 없는 값이면 역직렬화 단계에서 400이 난다(DB CHECK 대신 enum이 검증한다 —
 * {@link DeliveryCarrier} 참고). 직접 전달 등 택배사가 없는 경우를 위해 {@code ETC}가 있다.
 */
public record OrderShipRequest(

        @Schema(description = "택배사", example = "CJ")
        @NotNull
        DeliveryCarrier carrier,

        @Schema(description = "송장번호", example = "123456789012")
        @NotBlank @Size(max = 50)
        String trackingNo
) {
}
