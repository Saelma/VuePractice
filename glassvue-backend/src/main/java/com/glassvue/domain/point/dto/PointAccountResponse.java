package com.glassvue.domain.point.dto;

import com.glassvue.domain.point.entity.MemberGrade;
import com.glassvue.domain.point.entity.PointAccount;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 적립금·등급.
 *
 * <p>다음 등급까지 얼마 남았는지({@code amountToNextGrade})를 <b>서버가 계산해</b> 준다 —
 * 화면이 등급 임계값을 알 필요가 없다(쿠폰의 {@code discountPreview} 와 같은 판단).
 */
public record PointAccountResponse(
        @Schema(description = "사용 가능한 적립금", example = "1200") long balance,
        @Schema(description = "누적 구매확정액", example = "150000") long totalPurchase,
        @Schema(description = "현재 등급") MemberGrade grade,
        @Schema(description = "현재 적립률(%)", example = "2") int earnPercent,
        @Schema(description = "다음 등급") MemberGrade nextGrade,
        @Schema(description = "다음 등급까지 남은 구매액. 최고 등급이면 0", example = "350000") long amountToNextGrade
) {

    public static PointAccountResponse from(PointAccount account) {
        MemberGrade grade = account.getGrade();
        MemberGrade[] all = MemberGrade.values();
        boolean top = grade.ordinal() == all.length - 1;
        MemberGrade next = top ? grade : all[grade.ordinal() + 1];
        long remaining = top ? 0L : Math.max(0L, next.minPurchase() - account.getTotalPurchase());
        return new PointAccountResponse(account.getBalance(), account.getTotalPurchase(),
                grade, grade.earnPercent(), next, remaining);
    }
}
