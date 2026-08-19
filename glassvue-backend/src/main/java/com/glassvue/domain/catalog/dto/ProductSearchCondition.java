package com.glassvue.domain.catalog.dto;

import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.global.querydsl.Cond;
import com.glassvue.global.querydsl.Op;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 상품 검색 조건. 이름·가격·상태는 @Cond 자동, categoryId(연관)는 Repository에서 직접 처리.
 */
public record ProductSearchCondition(

        @Schema(description = "상품명 검색어")
        @Cond(op = Op.CONTAINS)
        String name,

        // 🔴 **@Cond 를 뗐다**(2026-08-19, G-5). 자동 처리는 `product.price` 컬럼을 그대로 보는데,
        //    기간 할인이 생기면서 그 값이 «지금 파는 가격» 이 아니게 됐다. 세일가로 걸러야 해서
        //    ProductRepositoryImpl 이 유효 판매가 식으로 직접 처리한다(categoryId 와 같은 탈출구).
        // ⚠ 애노테이션을 뗀 채로 Impl 에 안 적으면 **필터가 조용히 사라진다** — 400도 500도 안 나고
        //    그냥 «전부 나온다». 둘은 세트다.
        @Schema(description = "최소 가격(세일 중이면 세일가 기준)")
        Long minPrice,

        @Schema(description = "최대 가격(세일 중이면 세일가 기준)")
        Long maxPrice,

        @Schema(description = "상태")
        @Cond(op = Op.EQ)
        ProductStatus status,

        @Schema(description = "카테고리 id")
        UUID categoryId
) {
}
