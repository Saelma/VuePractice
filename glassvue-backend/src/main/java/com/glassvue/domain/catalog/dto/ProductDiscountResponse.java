package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.ProductDiscount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 기간 할인 한 줄 (2026-08-19, BACKLOG G-5).
 *
 * <p>⚠ <b>날짜를 두 벌로 준다.</b> {@code startDate}·{@code endDate} 는 관리자 폼이 그대로 다시 채울
 * <b>KST 날짜</b>(종료일 포함)이고, {@code startsAt}·{@code endsAt} 은 <b>실제 경계 시각</b>이다.
 * 폼이 {@code endsAt} 을 잘라 쓰면 <b>종료일이 하루 뒤로 보인다</b>(배타 경계라 25일 00:00 이다) —
 * 그 변환을 화면이 하게 두면 B-26 이 없앤 «경계를 두 곳에서 계산하는» 갈래가 되살아난다.
 *
 * <p>🔴 <b>{@code status} 는 서버가 정한다.</b> 화면이 «시작일이 오늘보다 뒤면 예정» 을 직접 계산하면
 * <b>브라우저 시계</b>를 기준으로 판단하게 된다(B-26 에서 「오늘」을 서버가 준 것과 같은 이유).
 */
public record ProductDiscountResponse(
        UUID id,
        @Schema(description = "할인율 %", example = "20") int rate,
        @Schema(description = "시작일 (KST, 포함)", example = "2026-08-22") LocalDate startDate,
        @Schema(description = "종료일 (KST, 포함)", example = "2026-08-24") LocalDate endDate,
        @Schema(description = "시작 경계(포함)") Instant startsAt,
        @Schema(description = "종료 경계(**배타**) — 종료일 다음 날 00:00 KST") Instant endsAt,
        @Schema(description = "UPCOMING(예정) · ACTIVE(진행 중) · ENDED(종료)", example = "ACTIVE")
        String status
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static ProductDiscountResponse from(ProductDiscount d, Instant now) {
        return new ProductDiscountResponse(
                d.getId(), d.getRate(),
                LocalDate.ofInstant(d.getStartsAt(), KST),
                // ⚠ 종료 경계는 **배타**라 하루를 빼야 «관리자가 적은 종료일» 로 돌아온다.
                //   이 한 줄을 빠뜨리면 폼을 다시 열 때마다 세일이 하루씩 길어진다.
                LocalDate.ofInstant(d.getEndsAt(), KST).minusDays(1),
                d.getStartsAt(), d.getEndsAt(),
                status(d, now));
    }

    private static String status(ProductDiscount d, Instant now) {
        if (d.isUpcomingAt(now)) {
            return "UPCOMING";
        }
        return d.isActiveAt(now) ? "ACTIVE" : "ENDED";
    }
}
