package com.glassvue.domain.coupon.dto;

import com.glassvue.domain.coupon.entity.Coupon;
import com.glassvue.domain.coupon.entity.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 프로모션 달력의 <b>막대 하나</b>(B-27, 관리자 전용).
 *
 * <p>🔴 <b>고객 배너({@link EventCouponResponse})와 데이터는 같지만 DTO 를 가른다.</b> 관리자 달력은
 * 앞으로 마케팅 대상 수·할인 원가 같은 값이 얹히는 자리이고, <b>그게 고객에게 새면 안 된다</b> —
 * 2026-08-06 에 고객용 문의 DTO 를 관리자 화면에 재사용한 사고의 <b>반대 방향</b>이다.
 *
 * <p>🔴 <b>날짜 경계는 서버가 만든다.</b> 화면이 UTC {@code Instant} 를 받아 자기 시간대로 자르면
 * <b>보는 사람마다 막대가 하루씩 밀린다.</b> 그래서 여기서 KST 로 잘라 «이 달의 며칠부터 며칠까지»
 * 를 정수로 준다(B-26·G-8 과 같은 원칙).
 */
public record PromotionSpanResponse(
        UUID couponId,
        String name,
        DiscountType discountType,
        long discountValue,

        @Schema(description = "이 막대가 무엇의 기간인가")
        Kind kind,

        @Schema(description = "이 달에서 막대가 시작하는 날(KST, 1-based)", example = "3")
        int startDay,
        @Schema(description = "이 달에서 막대가 끝나는 날(KST, 1-based, 포함)", example = "9")
        int endDay,

        @Schema(description = "지난달부터 이어져 온 막대인가 — 화면이 왼쪽을 열어 그린다")
        boolean continuesBefore,
        @Schema(description = "다음 달로 이어지는 막대인가")
        boolean continuesAfter,

        @Schema(description = "가입 쿠폰(V36)인가 — 상시라 달력에서는 배경처럼 다룬다")
        boolean welcome,

        /*
         * 🔴 **격자에 그릴지 위 스트립으로 뺄지를 가른다** (2026-08-13, 사용자 지적).
         *
         * 상시 쿠폰의 사용 기간은 대개 한 달을 꽉 채워서 **격자를 가로줄로 덮는다** — 위치가 없는
         * 값이라 격자에 있어 봐야 정보가 아니고, 정작 봐야 할 이벤트 겹침만 묻는다.
         *
         * ⚠ **화면이 «ISSUE 막대가 있나» 로 유추하지 않는다.** 발급 창이 지난달인 이벤트는 이 달에
         * USE 막대만 오므로 상시로 잘못 분류된다 — 서버가 아는 것을 서버가 말한다.
         */
        @Schema(description = "이벤트 쿠폰의 막대인가. false 면 상시 쿠폰이라 격자 밖에 둔다")
        boolean event
) {

    /**
     * ⚠ <b>둘을 나누는 것이 이 화면의 요점이다.</b> 겹치면 안 되는 것은 <b>발급 창</b>뿐이고
     * (그건 서버가 등록 때 막는다), 사용 기간은 <b>겹치는 게 정상</b>이다 — 한 색으로 그리면
     * 관리자가 «겹쳤다»를 사고로 읽는다.
     */
    public enum Kind {
        /** 발급 창 — 「받기」가 열려 있는 구간(이벤트 쿠폰만). */
        ISSUE,
        /** 사용 기간 — 받은 쿠폰을 쓸 수 있는 구간. */
        USE
    }

    /** 사용 기간 막대. 모든 쿠폰이 하나씩 가진다. */
    public static PromotionSpanResponse use(Coupon c, LocalDate first, LocalDate last, ZoneId zone) {
        return of(c, Kind.USE, c.getValidFrom(), c.getValidUntil(), first, last, zone);
    }

    /** 발급 창 막대. <b>이벤트 쿠폰에만</b> 있다(상시 쿠폰은 발급 창이라는 개념이 없다). */
    public static PromotionSpanResponse issue(Coupon c, LocalDate first, LocalDate last, ZoneId zone) {
        return of(c, Kind.ISSUE, c.getValidFrom(), c.getIssueUntil(), first, last, zone);
    }

    private static PromotionSpanResponse of(Coupon c, Kind kind, Instant from, Instant until,
                                            LocalDate first, LocalDate last, ZoneId zone) {
        LocalDate start = LocalDate.ofInstant(from, zone);
        LocalDate end = LocalDate.ofInstant(until, zone);
        // 이 달 밖으로 삐져나간 부분은 잘라서 준다 — 대신 잘렸다는 사실을 플래그로 남긴다.
        LocalDate clampedStart = start.isBefore(first) ? first : start;
        LocalDate clampedEnd = end.isAfter(last) ? last : end;
        return new PromotionSpanResponse(
                c.getId(), c.getName(), c.getDiscountType(), c.getDiscountValue(), kind,
                clampedStart.getDayOfMonth(), clampedEnd.getDayOfMonth(),
                start.isBefore(first), end.isAfter(last), c.isWelcome(), c.isEventCoupon());
    }
}
