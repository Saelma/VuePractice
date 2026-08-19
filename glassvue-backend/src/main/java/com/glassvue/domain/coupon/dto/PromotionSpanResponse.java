package com.glassvue.domain.coupon.dto;

import com.glassvue.domain.catalog.dto.ProductSaleResponse;
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
 *
 * <p>🔴 <b>2026-08-19(G-5)부터 이 막대는 쿠폰만의 것이 아니다</b> — 상품 기간 할인도 같은 격자에
 * 올라온다. 그래서 필드를 <b>중립화했다</b>:
 * <ul>
 *   <li>{@code couponId} → {@code id} — 쿠폰이면 쿠폰 id, 세일이면 할인 id.</li>
 *   <li>{@code discountType}·{@code discountValue} → <b>{@code label}</b>(서버가 만든 문구).
 *       ⚠ 쿠폰은 {@link DiscountType} enum 이고 세일은 %다 — <b>두 도메인의 표기 규칙</b>이라
 *       화면이 하나의 함수로 다루려면 화면이 양쪽을 다 알아야 한다. 날짜를 서버가 잘라 주는 것과
 *       <b>같은 이유</b>로 여기서 문구까지 만든다(달력은 조립물이다).
 *       ⚠ {@code couponDiscountText}(프론트)는 <b>그대로 남는다</b> — 쿠폰 화면 일곱 곳이 계속 쓴다.</li>
 *   <li>{@code event} → <b>{@code gridded}</b> — 뜻이 «이벤트 쿠폰인가» 가 아니라
 *       <b>«격자에 그릴 것인가»</b> 였다. 세일은 이벤트가 아닌데 격자에 그려야 해서 이름이 어긋났다.</li>
 * </ul>
 */
public record PromotionSpanResponse(
        @Schema(description = "쿠폰 id 또는 할인 id") UUID id,
        @Schema(description = "쿠폰명 또는 상품명", example = "몽쉘 10개") String name,
        @Schema(description = "할인 표기 — 서버가 만든다", example = "20% 할인") String label,

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
         * 값이라 격자에 있어 봐야 정보가 아니고, 정작 봐야 할 겹침만 묻는다.
         *
         * ⚠ **화면이 «ISSUE 막대가 있나» 로 유추하지 않는다.** 발급 창이 지난달인 이벤트는 이 달에
         * USE 막대만 오므로 상시로 잘못 분류된다 — 서버가 아는 것을 서버가 말한다.
         *
         * ⚠ **상품 세일은 언제나 true 다** — 세일에는 «상시» 라는 것이 없다(기간이 곧 정의다).
         */
        @Schema(description = "격자에 그릴 막대인가. false 면 상시 쿠폰이라 격자 밖 스트립에 둔다")
        boolean gridded
) {

    /**
     * ⚠ <b>셋을 나누는 것이 이 화면의 요점이다.</b> 겹치면 안 되는 것은 <b>발급 창</b>뿐이고
     * (그건 서버가 등록 때 막는다), 사용 기간은 <b>겹치는 게 정상</b>이다 — 한 색으로 그리면
     * 관리자가 «겹쳤다»를 사고로 읽는다.
     */
    public enum Kind {
        /** 발급 창 — 「받기」가 열려 있는 구간(이벤트 쿠폰만). */
        ISSUE,
        /** 사용 기간 — 받은 쿠폰을 쓸 수 있는 구간. */
        USE,
        /**
         * 상품 기간 할인(타임세일) — 그 상품이 싸게 팔리는 구간 (2026-08-19, G-5).
         *
         * <p>🔴 <b>이것도 겹치면 안 되는 종류다</b>(한 상품 안에서). 다만 발급 창과 달리
         * <b>상품마다 따로</b>라, 서로 다른 상품의 세일이 같은 날 겹치는 것은 정상이다.
         * ⚠ <b>쿠폰 발급 창과 세일이 겹치는 것</b>이 이 화면의 새 값어치다 — 그 날은
         * 쿠폰 할인이 <b>이미 깎인 세일가</b> 위에 또 먹는다(마진이 두 번 깎인다).
         */
        SALE
    }

    /** 사용 기간 막대. 모든 쿠폰이 하나씩 가진다. */
    public static PromotionSpanResponse use(Coupon c, LocalDate first, LocalDate last, ZoneId zone) {
        return ofCoupon(c, Kind.USE, c.getValidFrom(), c.getValidUntil(), first, last, zone);
    }

    /** 발급 창 막대. <b>이벤트 쿠폰에만</b> 있다(상시 쿠폰은 발급 창이라는 개념이 없다). */
    public static PromotionSpanResponse issue(Coupon c, LocalDate first, LocalDate last, ZoneId zone) {
        return ofCoupon(c, Kind.ISSUE, c.getValidFrom(), c.getIssueUntil(), first, last, zone);
    }

    /**
     * 상품 세일 막대 (2026-08-19, G-5).
     *
     * <p>⚠ <b>{@code endsAt} 은 배타 경계</b>(종료일 다음 날 00:00)라 <b>하루를 빼야</b>
     * «며칠까지» 가 된다. 안 빼면 달력에서 세일이 <b>하루 더 길어 보인다</b> —
     * 관리자 폼이 같은 자리에서 같은 실수를 할 수 있어 양쪽에 적어 둔다.
     */
    public static PromotionSpanResponse sale(ProductSaleResponse s,
                                             LocalDate first, LocalDate last, ZoneId zone) {
        return of(s.discountId(), s.productName(), s.rate() + "% 할인", Kind.SALE,
                s.startsAt(), LocalDate.ofInstant(s.endsAt(), zone).minusDays(1),
                first, last, zone, false, true);
    }

    private static PromotionSpanResponse ofCoupon(Coupon c, Kind kind, Instant from, Instant until,
                                                  LocalDate first, LocalDate last, ZoneId zone) {
        return of(c.getId(), c.getName(), discountText(c), kind,
                from, LocalDate.ofInstant(until, zone),
                first, last, zone, c.isWelcome(), c.isEventCoupon());
    }

    /** 쿠폰 할인 표기 — 프론트 {@code couponDiscountText} 와 같은 규칙이다. */
    private static String discountText(Coupon c) {
        return c.getDiscountType() == DiscountType.PERCENT
                ? c.getDiscountValue() + "% 할인"
                : String.format("%,d원 할인", c.getDiscountValue());
    }

    private static PromotionSpanResponse of(UUID id, String name, String label, Kind kind,
                                            Instant from, LocalDate end,
                                            LocalDate first, LocalDate last, ZoneId zone,
                                            boolean welcome, boolean gridded) {
        // ⚠ 시작·끝을 **같은 zone** 으로 자른다 — 한쪽만 다른 zone 이면 하루짜리 세일이
        //    이틀로 보이거나 사라진다. 그래서 zone 을 하드코딩하지 않고 인자로 받는다.
        LocalDate start = LocalDate.ofInstant(from, zone);
        // 이 달 밖으로 삐져나간 부분은 잘라서 준다 — 대신 잘렸다는 사실을 플래그로 남긴다.
        LocalDate clampedStart = start.isBefore(first) ? first : start;
        LocalDate clampedEnd = end.isAfter(last) ? last : end;
        return new PromotionSpanResponse(
                id, name, label, kind,
                clampedStart.getDayOfMonth(), clampedEnd.getDayOfMonth(),
                start.isBefore(first), end.isAfter(last), welcome, gridded);
    }
}
