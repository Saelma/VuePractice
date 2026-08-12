package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.entity.ProductVariant;
import com.glassvue.domain.image.dto.ImageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 상품 응답 (2026-07-24, C-8 로 재고 구조가 바뀜).
 *
 * <p>재고가 옵션(variant)으로 내려가면서 {@code stock} 단일 값이 사라졌다. 대신:
 * <ul>
 *   <li>{@code variants} — 옵션별 재고·가격·품절. 화면이 옵션 선택 UI 를 그린다(2개 이상일 때).</li>
 *   <li>{@code totalStock} — 옵션 재고 합. 목록에서 대략적인 재고를 보여줄 때.</li>
 *   <li>{@code soldOut} — 판매중이 아니거나 <b>모든 옵션이 품절</b>이면 true(목록 배지).</li>
 * </ul>
 * {@code price} 는 여전히 <b>기본가</b>다. 옵션 가격차는 각 variant 의 {@code price} 에 반영돼 있다.
 */
public record ProductResponse(
        UUID id,
        String name,
        // 카드 한 줄 카피(V33). null 이면 화면이 그 줄을 감춘다 — 기존 상품은 전부 null 이다.
        String tagline,
        String description,
        long price,
        // 정가(할인 전). null이면 할인 없음 — 할인율은 화면이 두 값에서 계산한다.
        Long listPrice,
        // 옵션 목록. 단일 옵션 상품이면 한 줄("기본")이다.
        List<VariantResponse> variants,
        long totalStock,
        // 판매중이 아니거나 옵션이 전부 품절이면 true. 목록의 "품절" 배지에 쓴다.
        boolean soldOut,
        ProductStatus status,
        UUID categoryId,
        String categoryName,
        List<ImageResponse> images,
        double averageRating,
        long reviewCount,
        // 누적 판매량(비정규화, V25). 홈 "인기순" 정렬 기준이자 "N개 판매" 표시에 쓴다.
        long soldCount,
        /**
         * 삭제 대기 여부 (2026-08-12, F-7). 🔴 <b>목록·상세에서는 항상 false</b> 다 —
         * 그쪽은 대기 상품을 아예 안 돌려주기 때문이다. 이 값이 참으로 오는 자리는
         * <b>«대기 중인 것도 함께 읽는» 호출부</b>뿐이다: 장바구니(줄을 남기려고)와
         * 리뷰 관리 목록(유예 중에는 상품명이 살아 있어야 한다).
         * ⚠ 그래서 이 플래그는 «화면에 보여줄 값» 이 아니라 <b>«구매를 막을 근거»</b> 다.
         */
        boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {
    /** 옵션·이미지 없이 (일부 내부 용도만). 실사용은 variants 를 넘기는 아래 팩토리다. */
    public static ProductResponse from(Product p) {
        return from(p, List.of(), List.of());
    }

    public static ProductResponse from(Product p, List<ProductVariant> variants, List<ImageResponse> images) {
        List<VariantResponse> variantResponses = variants.stream()
                .map(v -> VariantResponse.from(v, p.getPrice()))
                .toList();
        long totalStock = variants.stream().mapToLong(ProductVariant::getStock).sum();
        boolean soldOut = p.getStatus() != ProductStatus.SELLING
                || variants.stream().noneMatch(v -> v.getStock() > 0);
        return new ProductResponse(
                p.getId(), p.getName(), p.getTagline(), p.getDescription(), p.getPrice(), p.getListPrice(),
                variantResponses, totalStock, soldOut,
                p.getStatus(), p.getCategory().getId(), p.getCategory().getName(),
                images, p.getAvgRating(), p.getReviewCount(), p.getSoldCount(),
                p.isDeleted(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
