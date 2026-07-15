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

        @Schema(description = "최소 가격")
        @Cond(path = "price", op = Op.GOE)
        Long minPrice,

        @Schema(description = "최대 가격")
        @Cond(path = "price", op = Op.LOE)
        Long maxPrice,

        @Schema(description = "상태")
        @Cond(op = Op.EQ)
        ProductStatus status,

        @Schema(description = "카테고리 id")
        UUID categoryId
) {
}
