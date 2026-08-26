package com.glassvue.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 상품별 판매 실적 한 줄.
 *
 * <p>상품명은 <b>주문 시점 스냅샷</b>이고, 이름이 바뀐 상품은 가장 최근 결제 건의 이름으로 보여준다
 * (묶는 기준은 {@code productId}). catalog 를 조회하지 않으므로 도메인 의존이 생기지 않는다.
 *
 * <p>⚠ {@code sales} 는 <b>쿠폰 할인 전</b>이다. 쿠폰은 주문 단위로 붙어서 어느 상품이 얼마를 깎았는지
 * 나눌 근거가 없다 — 안분하면 그럴듯하지만 지어낸 숫자가 된다.
 * 그래서 이 값들의 합계는 요약의 {@code itemSales}(할인 후)와 <b>일부러 다르다.</b>
 *
 * <p>🔴 <b>수량·판매액은 «남은 것» 이다</b> (2026-08-26, BACKLOG §I-5) — 취소·반품으로 빠진 몫은
 * 빠진 뒤의 값이다. ⚠ 주문 시점 스냅샷({@code order_item.quantity})이 아니라는 뜻이다.
 * 식은 {@code OrderStatsRepository.REMAINING_QUANTITY}·{@code REMAINING_AMOUNT} 한 곳에 있다.
 *
 * <p>⚠ 🔴 <b>상점의 인기순({@code product.sold_count})과 «같은 값» 이 되는 것은 아니다</b>
 * (2026-08-26 배포 검증에서 실측). <b>세는 창이 다르다</b> — 이 값은 «고른 기간 · 매출 상태» 이고
 * 저쪽은 <b>전체 기간 누적</b>이다. 게다가 {@code sold_count} 에는 <b>2026-08-25 이전 부분 취소가
 * 판매량을 안 되돌리던 구멍</b>의 이력이 남아 있다(실측: 지바 TOP 5 / {@code sold_count} 12).
 * 🔴 <b>둘을 나란히 놓고 «어느 쪽이 맞나» 를 따지지 말 것</b> — 다른 질문에 답하는 값이다.
 */
public record ProductSalesResponse(
        @Schema(description = "상품 id") UUID productId,
        @Schema(description = "상품명 (주문 시점 스냅샷)", example = "몽쉘") String productName,
        @Schema(description = "판매 수량 — **취소·반품분을 뺀 남은 수량**", example = "12") long quantity,
        @Schema(description = "판매액 (쿠폰 할인 전) — **취소·반품분을 뺀 남은 금액**", example = "120000") long sales
) {
}
