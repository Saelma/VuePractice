package com.glassvue.domain.point.dto;

import com.glassvue.domain.point.entity.MemberGrade;
import com.glassvue.domain.point.entity.PointAccount;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 적립금·등급.
 *
 * <p>다음 등급까지 얼마 남았는지({@code amountToNextGrade})를 <b>서버가 계산해</b> 준다 —
 * 화면이 등급 임계값을 알 필요가 없다(쿠폰의 {@code discountPreview} 와 같은 판단).
 * <b>무료배송 기준({@code freeShippingThreshold})도 같은 이유로 서버가 낸다</b> — 화면은
 * 「기본 30,000원」도 「등급별 인하율」도 모른다 (2026-08-28, BACKLOG G-6).
 */
public record PointAccountResponse(
        @Schema(description = "사용 가능한 적립금", example = "1200") long balance,
        @Schema(description = "누적 구매확정액", example = "150000") long totalPurchase,
        @Schema(description = "현재 등급") MemberGrade grade,
        @Schema(description = "현재 적립률(%)", example = "2") int earnPercent,
        @Schema(description = "다음 등급") MemberGrade nextGrade,
        @Schema(description = "다음 등급까지 남은 구매액. 최고 등급이면 0", example = "350000") long amountToNextGrade,
        @Schema(description = "이 등급의 무료배송 기준 금액. 0이면 무료배송 정책 없음", example = "24000")
        long freeShippingThreshold
) {

    /**
     * @param baseFreeThreshold 설정의 기본 무료배송 기준 — 호출자(서비스)가 정책에서 읽어 넘긴다.
     *                          🔴 <b>DTO 가 정책 빈을 직접 부르지 않는다</b>(static 이라 부를 수도 없고,
     *                          그래야 이 record 가 «값만 담는 것» 으로 남는다).
     */
    public static PointAccountResponse from(PointAccount account, long baseFreeThreshold) {
        MemberGrade grade = account.getGrade();
        MemberGrade[] all = MemberGrade.values();
        boolean top = grade.ordinal() == all.length - 1;
        MemberGrade next = top ? grade : all[grade.ordinal() + 1];
        long remaining = top ? 0L : Math.max(0L, next.minPurchase() - account.getTotalPurchase());
        return new PointAccountResponse(account.getBalance(), account.getTotalPurchase(),
                grade, grade.earnPercent(), next, remaining,
                grade.discountedThreshold(baseFreeThreshold));
    }
}
