package com.glassvue.domain.catalog.controller;

import com.glassvue.domain.catalog.dto.ProductCreateRequest;
import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.dto.ProductSearchCondition;
import com.glassvue.domain.catalog.dto.ProductUpdateRequest;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "Product", description = "상품 API")
public interface ProductController {

    @Operation(summary = "상품 등록 (관리자)",
            description = "옵션의 **초기 재고가 재고 이력의 첫 줄**로 남는다(B-19) — 행위자는 로그인한 관리자다.")
    ResponseEntity<ApiResponse<UUID>> create(@Parameter(hidden = true) AuthUser user,
            @Valid ProductCreateRequest request);

    @Operation(summary = "상품 목록/검색 (이름·가격·상태·카테고리, 페이징)")
    ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> search(
            @ParameterObject ProductSearchCondition condition,
            @ParameterObject Pageable pageable);

    @Operation(summary = "상품 단건 조회")
    ResponseEntity<ApiResponse<ProductResponse>> get(UUID id);

    @Operation(summary = "상품 수정 (관리자)",
            description = """
                    옵션은 **통째로 교체**된다(보낸 목록이 곧 전체다).

                    재고가 달라진 옵션만 **재고 이력**에 남는다(B-19) — 옵션명으로 전/후를 대조하며,
                    **변동이 0이면 남기지 않는다**(상품명만 고쳐도 옵션은 다시 만들어지기 때문).
                    사라진 옵션은 그 재고만큼 감소로, 새로 생긴 옵션은 그 재고만큼 증가로 기록된다.
                    """)
    ResponseEntity<ApiResponse<Void>> update(@Parameter(hidden = true) AuthUser user,
            UUID id, @Valid ProductUpdateRequest request);

    @Operation(summary = "상품 삭제 (관리자) — **유예 후 삭제**",
            description = """
                    🔴 **바로 지우지 않는다**(2026-08-12, F-7). 상품은 **삭제 대기**로 표시되고
                    유예 기간이 지나면 배치가 진짜로 지운다. 그 사이에는 관리자 화면에서 **복구**할 수 있다.

                    대기 중인 상품은 목록·검색·상세에서 빠지고 새 리뷰·문의도 못 단다.
                    ⚠ 다만 **장바구니에서는 줄이 사라지지 않는다** — 구매만 막히고, 복구되면 되살아난다.
                    """)
    ResponseEntity<ApiResponse<Void>> delete(@Parameter(hidden = true) AuthUser user, UUID id);
}
