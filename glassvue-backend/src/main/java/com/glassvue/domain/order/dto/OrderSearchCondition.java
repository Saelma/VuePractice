package com.glassvue.domain.order.dto;

import com.glassvue.domain.order.entity.OrderStatus;
import com.glassvue.global.querydsl.Cond;
import com.glassvue.global.querydsl.Op;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 주문 검색 조건.
 *
 * <p>{@code memberId}는 화면에서 받는 값이 아니라 **서버가 채워 넣는 스코프**다 —
 * 사용자용 "내 주문"은 본인 id를, 관리자 목록은 null(전체)을 넘긴다.
 * 덕분에 리포지토리는 하나로 두고 호출부에서 범위만 정하면 된다.
 */
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

        @Schema(hidden = true) // 클라이언트가 지정하지 못한다 — 남의 주문 조회 방지
        @Cond(op = Op.EQ)
        UUID memberId
) {
    /** 관리자용 — 전체 주문 대상. */
    public OrderSearchCondition forAll() {
        return new OrderSearchCondition(status, buyer, orderNo, null);
    }

    /** 사용자용 — 본인 주문으로 범위를 좁힌다. 구매자 검색은 의미가 없으므로 버린다. */
    public OrderSearchCondition scopedTo(UUID ownerId) {
        return new OrderSearchCondition(status, null, null, ownerId);
    }
}
