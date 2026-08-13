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
        Integer daysUntil,

        /*
         * 🔴 **「다음이 있다」를 함께 말한다** (2026-08-13, 사용자 요청 — 검증 중에 나왔다).
         *
         * 배너가 하나만 보여주면 «이번을 놓치면 끝» 처럼 읽힌다. 쿠폰의 목적이 **다시 오게 하는 것**인데
         * 그 다음 약속이 화면에 없었다.
         *
         * ⚠ 그렇다고 이벤트를 줄줄이 늘어놓지 않는다(사용자도 그건 아니라고 못 박았다) — **개수와
         * 가장 가까운 하나**만 말한다. 목록이 되는 순간 배너가 아니라 페이지가 된다.
         */
        @Schema(description = "이 배너가 가리키는 것 **말고** 앞으로 더 있는 이벤트 수", example = "2")
        int moreUpcoming,

        @Schema(description = "그중 가장 가까운 것까지 남은 날(KST). 더 없으면 null", example = "7")
        Integer nextDaysUntil
) {

    /** 오늘 진행 중인 이벤트. {@code moreUpcoming} 은 오늘 것 말고 앞으로 예정된 전부다. */
    public static EventCouponResponse open(Coupon c, boolean claimed, int moreUpcoming, Integer nextDaysUntil) {
        return of(c, true, claimed, null, moreUpcoming, nextDaysUntil);
    }

    /** 앞으로 있을 이벤트(예고). {@code moreUpcoming} 은 <b>이 배너가 가리키는 것을 뺀</b> 나머지다. */
    public static EventCouponResponse upcoming(Coupon c, int daysUntil, int moreUpcoming, Integer nextDaysUntil) {
        return of(c, false, false, daysUntil, moreUpcoming, nextDaysUntil);
    }

    private static EventCouponResponse of(Coupon c, boolean open, boolean claimed, Integer daysUntil,
                                          int moreUpcoming, Integer nextDaysUntil) {
        return new EventCouponResponse(
                c.getId(), c.getName(), c.getDiscountType(), c.getDiscountValue(),
                c.getMinOrderAmount(), c.getMaxDiscountAmount(),
                c.getIssueUntil(), c.getValidUntil(), open, claimed, daysUntil,
                moreUpcoming, nextDaysUntil);
    }
}
