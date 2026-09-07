package com.glassvue.domain.order.dto;

import com.glassvue.domain.order.entity.OrderStatus;
import com.glassvue.global.querydsl.Cond;
import com.glassvue.global.querydsl.Op;
import com.glassvue.global.querydsl.Transform;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

public record OrderSearchCondition(

        @Schema(description = "주문 상태 필터. 비우면 전체")
        @Cond(op = Op.EQ)
        OrderStatus status,

        @Schema(description = "구매자 닉네임 검색어(관리자 목록용)")
        @Cond(path = "buyerNickname", op = Op.CONTAINS)
        String buyer,

        @Schema(description = "주문번호 검색어(관리자 목록용). CS에서 고객이 불러준 번호로 찾는다")
        @Cond(path = "orderNo", op = Op.CONTAINS)
        String orderNo,

        /*
         * 기간(B-26). ⚠ **날짜만 받는다** — Instant 로 받으면 「그 날의 00:00 이 언제인가」가
         * 화면과 서버 두 곳에서 계산되고, 하루가 어긋나도 화면은 멀쩡해 보인다.
         * 경계는 Transform → KstDates 한 곳이 만든다.
         */
        @Schema(description = "주문일 시작(yyyy-MM-dd). **그 날 포함**", example = "2026-09-01")
        @Cond(path = "createdAt", op = Op.GOE, transform = Transform.DATE_START)
        LocalDate from,

        @Schema(description = "주문일 종료(yyyy-MM-dd). **그 날 포함** — 내부적으로 다음 날 00:00 미만이 된다",
                example = "2026-09-07")
        @Cond(path = "createdAt", op = Op.LT, transform = Transform.DATE_NEXT)
        LocalDate to,

        @Schema(hidden = true) // 클라이언트가 지정하지 못한다 — 남의 주문 조회 방지
        @Cond(op = Op.EQ)
        UUID memberId
) {
    public OrderSearchCondition forAll() {
        return new OrderSearchCondition(status, buyer, orderNo, from, to, null);
    }

    /**
     * 본인 주문으로 좁힌다.
     *
     * <p>⚠ <b>기간은 남긴다.</b> {@code buyer}·{@code orderNo} 를 버리는 이유는 그것이
     * <b>남의 주문을 넘겨다보는 수단</b>이라서인데, 기간은 그렇지 않다 —
     * 🔴 {@code memberId} 가 이미 범위를 잠그므로 기간은 그 안에서만 좁힌다.
     */
    public OrderSearchCondition scopedTo(UUID ownerId) {
        return new OrderSearchCondition(status, null, null, from, to, ownerId);
    }
}
