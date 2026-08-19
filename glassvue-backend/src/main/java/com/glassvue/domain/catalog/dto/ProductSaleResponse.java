package com.glassvue.domain.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * 어떤 기간에 걸치는 <b>상품 세일 하나</b> — catalog 가 <b>다른 도메인에 내주는 공개 계약</b>
 * (2026-08-19, B-27 에 타임세일을 얹으면서 생겼다).
 *
 * <p>🔴 <b>이 record 가 있는 이유가 도메인 경계다.</b> 프로모션 달력은 coupon 쪽에 있는데
 * 상품 세일을 함께 그려야 한다. 그렇다고 달력이 {@code ProductDiscount} 엔티티나
 * {@code ProductDiscountRepository} 를 직접 만지면 <b>catalog 를 폴더째 들어낼 수 없게 된다</b>
 * (CLAUDE.md — 도메인 간 직접 참조 금지). 장바구니·찜이 {@code ProductResponse} 로만 상품을 읽는
 * 것과 같은 방식이다.
 *
 * <p>⚠ <b>{@code productName} 을 함께 싣는 것이 요점이다.</b> 이게 없으면 호출한 쪽이 상품 이름을
 * 얻으려고 catalog 를 <b>한 번 더</b> 부르게 되고(달력이면 세일 수만큼), 그 순간 N+1 이 된다.
 *
 * <p>⚠ <b>삭제 대기 상품의 세일은 애초에 여기 안 담긴다</b>(F-7). 목록에 안 나오는 상품이라
 * 달력에 그려 봐야 관리자만 «이게 왜 여기 있지» 를 본다.
 *
 * @param endsAt <b>배타 경계</b>다 — 종료일 다음 날 00:00(KST). 「며칠까지」로 보이려면 하루를 빼야 한다.
 */
public record ProductSaleResponse(
        UUID discountId,
        UUID productId,
        @Schema(description = "상품명", example = "몽쉘 10개") String productName,
        @Schema(description = "할인율 %", example = "20") int rate,
        Instant startsAt,
        Instant endsAt
) {
}
