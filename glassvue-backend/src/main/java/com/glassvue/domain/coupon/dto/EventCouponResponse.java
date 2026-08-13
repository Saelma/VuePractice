package com.glassvue.domain.coupon.dto;

import com.glassvue.domain.coupon.entity.Coupon;
import com.glassvue.domain.coupon.entity.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * 이벤트 쿠폰 배너 한 장(G-8). <b>화면이 무엇을 그릴지는 이 응답이 정한다</b> —
 * 줄 게 없으면 서버가 {@code null} 을 주고 배너 자체가 안 그려진다.
 *
 * <p>배너가 말하는 것은 <b>둘 중 하나</b>이고 섞지 않는다:
 * <ul>
 *   <li>{@code open=true} — 오늘이 이벤트 날. 「받기」(또는 이미 받았으면 「받음」).
 *   <li>{@code open=false} — 예고. *"다음 이벤트 D-3"*, <b>행동을 요구하지 않는다.</b>
 * </ul>
 *
 * <p>🔴 <b>{@code daysUntil} 은 서버가 센다.</b> 클라이언트 시계·시간대로 계산하면
 * <b>어떤 사람에게만 D-1 이 D-2 로 보인다</b> — KST 경계를 한 곳에서만 만든다는
 * 원칙(B-26 과 같은 자리)이다.
 *
 * <p>⚠ 비로그인도 부를 수 있는 공개 정보다. 다만 <b>비로그인 화면은 {@code open=true} 일 때만</b>
 * 이 값을 쓴다 — 예고를 보고 가입해도 그 날 다시 와야 받으므로, 예고는 어긋난 약속이 된다
 * (BACKLOG G-8). {@code claimed} 는 비로그인에게 언제나 {@code false} 다.
 */
public record EventCouponResponse(
        UUID couponId,
        String name,
        DiscountType discountType,
        long discountValue,
        long minOrderAmount,
        Long maxDiscountAmount,

        @Schema(description = "발급 마감 시각") Instant issueUntil,
        @Schema(description = "사용 마감 시각 — 발급 마감보다 보통 훨씬 뒤다") Instant validUntil,

        @Schema(description = "지금 받을 수 있나(발급 창이 열려 있나)") boolean open,
        @Schema(description = "이미 받았나. 비로그인은 언제나 false") boolean claimed,

        @Schema(description = "예고일 때 남은 날(KST 기준). 오늘 진행 중이면 null", example = "3")
        Integer daysUntil
) {

    /** 오늘 진행 중인 이벤트. */
    public static EventCouponResponse open(Coupon c, boolean claimed) {
        return of(c, true, claimed, null);
    }

    /** 앞으로 있을 이벤트(예고). */
    public static EventCouponResponse upcoming(Coupon c, int daysUntil) {
        return of(c, false, false, daysUntil);
    }

    private static EventCouponResponse of(Coupon c, boolean open, boolean claimed, Integer daysUntil) {
        return new EventCouponResponse(
                c.getId(), c.getName(), c.getDiscountType(), c.getDiscountValue(),
                c.getMinOrderAmount(), c.getMaxDiscountAmount(),
                c.getIssueUntil(), c.getValidUntil(), open, claimed, daysUntil);
    }
}
