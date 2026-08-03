package com.glassvue.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 마케팅 알림 발송 요청 (2026-08-03, B-21 후속).
 *
 * <p>⚠ <b>대상을 요청에서 받지 않는다.</b> 누구에게 보낼지는 서버가 정한다 —
 * 「마케팅 동의자」이고, 그중 알림 설정을 끈 사람은 빠진다. 대상을 클라이언트가 지정할 수 있게 하면
 * <b>동의하지 않은 회원에게도 보낼 수 있는 구멍</b>이 된다(동의를 받는 기능을 만들어 놓고
 * 그걸 우회하는 문을 여는 셈).
 */
public record MarketingSendRequest(

        @Schema(description = "알림 제목", example = "여름 감사 쿠폰 안내")
        @NotBlank @Size(max = 100)
        String title,

        @Schema(description = "알림 내용", example = "이번 주말까지 전 상품 무료배송입니다.")
        @NotBlank @Size(max = 500)
        String message,

        /*
         * 눌렀을 때 갈 곳. 비워 두면 링크 없는 알림이 된다.
         * ⚠ **앱 안 경로만** 넣는다(`/products/…`). 외부 URL 로 보내지 않는 건 이 프로젝트의 방침이다
         * (배송 조회를 실제 택배사 대신 /mock-tracking 으로 둔 것과 같은 이유).
         */
        @Schema(description = "이동 경로 (앱 내부 경로, 선택)", example = "/products")
        @Size(max = 200)
        String link
) {
}
