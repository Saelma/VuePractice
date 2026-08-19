package com.glassvue.domain.wishlist.dto;

import com.glassvue.domain.catalog.entity.ProductStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * 찜 목록 한 줄 — 상품 카드로 그릴 수 있을 만큼만 담는다.
 *
 * <p>찜 자체({@code wishlist} 행)에는 상품 id 밖에 없고, 나머지는 전부 catalog 에서 <b>지금</b> 읽어
 * 합성한 값이다. 주문(스냅샷)과 정반대의 판단인데 이유가 있다 — 찜은 "나중에 살까" 하고 담아 두는
 * 것이라 <b>가격이 내렸는지·품절됐는지 지금 값</b>을 봐야 쓸모가 있다. 찜한 시점의 가격을 박아 두면
 * 오히려 틀린 정보가 된다.
 *
 * <p>⚠ <b>{@code regularPrice}·{@code discountRate} 도 함께 나른다</b>(2026-08-19, G-5).
 * {@code price} 에는 기간 할인이 이미 반영돼 있는데 그 둘이 없으면 <b>화면이 「세일 중」인 것을
 * 모른다</b> — 취소선도 배지도 안 뜨고 <b>그냥 싼 상품</b>으로 보인다. 그건 이 DTO 가 스냅샷 대신
 * «지금 값» 을 고른 이유(«가격이 내렸는지 봐야 쓸모가 있다»)와 정면으로 어긋난다.
 *
 * @param available 지금 살 수 있는지(판매중 + 재고 있음). 품절이어도 찜은 유지된다 — 재입고를 기다리는 게 찜의 용도다
 * @param addedAt   찜한 시각. 목록 정렬 기준이고 "언제 담았더라"를 화면에 보여줄 수 있다
 */
public record WishlistItemResponse(
        UUID productId,
        String name,
        long price,
        long regularPrice,
        Integer discountRate,
        Long listPrice,
        ProductStatus status,
        String thumbUrl,
        double averageRating,
        long reviewCount,
        boolean available,
        Instant addedAt
) {
}
