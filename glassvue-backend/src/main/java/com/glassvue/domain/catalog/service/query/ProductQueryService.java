package com.glassvue.domain.catalog.service.query;

import com.glassvue.domain.catalog.config.CatalogProperties;
import com.glassvue.domain.catalog.dto.LowStockItemResponse;
import com.glassvue.domain.catalog.dto.LowStockResponse;
import com.glassvue.domain.catalog.dto.ProductDiscountResponse;
import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.dto.ProductSaleResponse;
import java.time.Duration;
import com.glassvue.domain.catalog.dto.DeletedProductResponse;
import com.glassvue.domain.catalog.dto.ProductSearchCondition;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductDiscount;
import com.glassvue.domain.catalog.entity.ProductVariant;
import com.glassvue.domain.catalog.repository.ProductDiscountRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.repository.ProductVariantRepository;
import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.response.PageResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

    /**
     * 대시보드 「재고 부족」 목록에 실어 보내는 줄 수. 카드 한 장에 들어가는 만큼만 준다 —
     * 전체 건수는 {@code count} 로 따로 가므로 목록이 잘려도 숫자는 거짓말하지 않는다.
     */
    private static final int LOW_STOCK_ITEMS = 8;

    private final ProductRepository productRepository;
    private final ProductDiscountRepository discountRepository;
    private final ProductVariantRepository variantRepository;
    private final ImageService imageService;
    private final CatalogProperties catalogProperties;

    /**
     * 상품 상세.
     *
     * <p>🔴 <b>삭제 대기 상품은 없는 것으로 답한다</b>(2026-08-12, F-7). 목록에서 뺐어도
     * <b>URL 을 직접 치면 열린다</b> — 알림·북마크·검색엔진에 남은 링크가 그 경로다.
     * ⚠ 「삭제 대기라서 못 본다」가 아니라 <b>404</b> 인 이유: 고객에게는 «없는 상품» 이 맞고,
     * 관리자는 복구 화면에서 본다(그쪽은 이 메서드를 안 탄다).
     */
    public ProductResponse get(UUID id) {
        Product product = productRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        List<ProductVariant> variants =
                variantRepository.findByProductIdOrderBySortOrderAscCreatedAtAsc(id);
        return ProductResponse.from(product, variants, imageService.findByGroup(product.getImageGroupId()),
                activeDiscountsOf(List.of(product)).get(id));
    }

    /**
     * 상품 존재 확인 (리뷰·문의 등 타 도메인이 상품에 종속 리소스를 만들 때). 없으면 PRODUCT_NOT_FOUND.
     *
     * <p>⚠ <b>삭제 대기 상품에는 새 리뷰·문의를 달 수 없다</b>(2026-08-12, F-7) — 곧 사라질 상품에
     * 글을 남기게 하면 <b>답변자도 없는 문의</b>가 생긴다. 이미 달린 것은 그대로 남는다(느슨한 참조).
     */
    public void ensureExists(UUID id) {
        if (!productRepository.existsAliveById(id)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    /**
     * 여러 상품을 한 번에 조회 (장바구니 등 타 도메인용). 옵션·이미지도 함께 싣는다.
     * 옵션·이미지 그룹 모두 배치로 조회해 상품 수만큼 쿼리가 늘지 않게 한다(N+1 회피).
     */
    public List<ProductResponse> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Product> products = productRepository.findAllById(ids);
        Map<UUID, List<ProductVariant>> variantsByProduct = variantsOf(products);
        Map<UUID, List<ImageResponse>> imagesByGroup = imagesOf(products);
        Map<UUID, ProductDiscount> discounts = activeDiscountsOf(products);
        return products.stream()
                .map(p -> ProductResponse.from(p,
                        variantsByProduct.getOrDefault(p.getId(), List.of()),
                        imagesFor(p, imagesByGroup),
                        discounts.get(p.getId())))
                .toList();
    }

    @Cacheable(cacheNames = "products:list", key = "#condition.toString() + '|' + #pageable.toString()")
    public PageResponse<ProductResponse> search(ProductSearchCondition condition, Pageable pageable) {
        Page<Product> page = productRepository.search(condition, pageable);
        List<Product> products = page.getContent();
        Map<UUID, List<ProductVariant>> variantsByProduct = variantsOf(products);
        Map<UUID, List<ImageResponse>> imagesByGroup = imagesOf(products);
        Map<UUID, ProductDiscount> discounts = activeDiscountsOf(products);

        Page<ProductResponse> mapped = page.map(p -> ProductResponse.from(p,
                variantsByProduct.getOrDefault(p.getId(), List.of()),
                imagesFor(p, imagesByGroup),
                discounts.get(p.getId())));
        return PageResponse.from(mapped);
    }


    /**
     * 지금 유효한 할인을 상품별로 하나씩 — <b>세일가가 만들어지는 유일한 입구</b> (2026-08-19, G-5).
     *
     * <p>🔴 <b>상품당 하나로 줄이는 것이 이 메서드의 일이다.</b> 기간 겹침은 Oracle 유니크로 못 막아
     * 앱이 유일한 방어인데(V52), 그 방어가 뚫리면 한 상품에 유효한 할인이 둘 이상 있게 된다.
     * G-8 에서 «열린 이벤트 둘» 이 홈 전체를 500 으로 만들 뻔한 것과 같은 자리라 —
     * <b>여기서 죽지 않고 하나를 고른다.</b>
     *
     * <p>⚠ 고르는 기준은 <b>할인율이 가장 높은 것</b>이다(리포지토리가 그 순서로 준다).
     * 고객에게 유리한 쪽이 사고가 덜 난다 — 더 비싸게 청구하는 것보다 낫다.
     * ⚠ 목록 정렬·가격필터가 쓰는 SQL 도 {@code max(rate)} 로 같은 것을 고른다. <b>둘이 갈리면
     * 「1만원 이하」로 걸러 놓고 목록엔 1만원 넘는 값이 뜬다.</b>
     *
     * <p>🔴 <b>뚫렸으면 로그를 남긴다.</b> 조용히 하나를 고르면 겹침이 영원히 안 보인다 —
     * 화면은 멀쩡하고 아무도 모르는 채로 «어느 할인이 먹었는지» 만 달라진다.
     */
    private Map<UUID, ProductDiscount> activeDiscountsOf(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = products.stream().map(Product::getId).toList();
        Map<UUID, ProductDiscount> chosen = new java.util.HashMap<>();
        for (ProductDiscount d : discountRepository.findActive(ids, Instant.now())) {
            ProductDiscount prev = chosen.putIfAbsent(d.getProductId(), d);
            if (prev != null) {
                // ⚠ 여기 왔다는 것은 등록·수정의 겹침 가드가 뚫렸다는 뜻이다(동시 등록 등).
                //    적용된 것과 무시된 것을 **둘 다** 적는다 — 하나만 적으면 어느 쪽이 문제인지 모른다.
                log.warn("[상품] 기간이 겹치는 할인이 둘 이상이다 — productId={} 적용={}% ({}~{}) 무시={}% ({}~{})",
                        d.getProductId(), prev.getRate(), prev.getStartsAt(), prev.getEndsAt(),
                        d.getRate(), d.getStartsAt(), d.getEndsAt());
            }
        }
        return chosen;
    }


    /**
     * 한 상품의 할인 일정 전부 — 관리자 화면 (2026-08-19, G-5).
     *
     * <p>⚠ <b>지난 것도 함께 준다.</b> 「지금 세일 중인가」만 보여주면 관리자는 <b>다음 세일을 언제
     * 걸어야 겹치지 않는지</b>를 알 수 없고, 겹침 거절(4xx)을 만난 뒤에야 무엇과 겹쳤는지 찾게 된다.
     * 시간순으로 죽 늘어놓는 것이 그 질문에 답한다.
     */
    public List<ProductDiscountResponse> discountsOf(UUID productId) {
        Instant now = Instant.now();
        return discountRepository.findByProductIdOrderByStartsAtAsc(productId).stream()
                .map(d -> ProductDiscountResponse.from(d, now))
                .toList();
    }


    /**
     * 어떤 기간에 걸치는 상품 세일 — <b>catalog 가 다른 도메인에 내주는 공개 API</b>
     * (2026-08-19, B-27 프로모션 달력).
     *
     * <p>🔴 <b>이 메서드가 도메인 경계를 지킨다.</b> 달력은 coupon 쪽에 있는데 상품 세일도 그려야 한다 —
     * 엔티티·리포지토리를 넘겨주면 catalog 를 폴더째 들어낼 수 없게 되므로, 여기서 <b>DTO 로만</b> 준다
     * (장바구니가 {@link #findByIds} 만 쓰는 것과 같은 방식).
     *
     * <p>⚠ 경계는 <b>호출한 쪽이 만들어서 넘긴다.</b> 달력의 「8월」은 KST 의 8월이고 그 판단은
     * 달력이 한다 — 여기서 다시 자르면 <b>경계가 두 곳</b>에 생긴다(B-26 이 없앤 갈래).
     */
    public List<ProductSaleResponse> salesBetween(Instant from, Instant to) {
        return discountRepository.findSalesBetween(from, to);
    }

    /** 상품들의 옵션을 한 번에 조회해 productId 로 묶는다(정렬 유지). */
    private Map<UUID, List<ProductVariant>> variantsOf(List<Product> products) {
        List<UUID> productIds = products.stream().map(Product::getId).toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return variantRepository.findByProductIdInOrderBySortOrderAscCreatedAtAsc(productIds).stream()
                .collect(Collectors.groupingBy(ProductVariant::getProductId));
    }

    private Map<UUID, List<ImageResponse>> imagesOf(List<Product> products) {
        List<UUID> groupIds = products.stream()
                .map(Product::getImageGroupId).filter(Objects::nonNull).toList();
        return imageService.findByGroups(groupIds);
    }

    private List<ImageResponse> imagesFor(Product p, Map<UUID, List<ImageResponse>> imagesByGroup) {
        return p.getImageGroupId() == null
                ? List.of()
                : imagesByGroup.getOrDefault(p.getImageGroupId(), List.of());
    }

    /**
     * 옵션 id → 상품 id 매핑 (장바구니 등 타 도메인용).
     *
     * <p>장바구니는 옵션(variant) 단위로 담기지만, 상품 정보는 {@link #findByIds} 로 상품 단위로 가져온다.
     * 그 사이를 잇는 최소 primitive 다 — 이걸 노출해 cart 가 catalog 리포지토리를 직접 만지지 않게 한다.
     * 존재하지 않는 옵션(삭제됨)은 결과에서 빠진다(호출부가 장바구니에서 정리).
     */
    public java.util.Map<UUID, UUID> productIdsOfVariants(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        return variantRepository.findAllById(variantIds).stream()
                .collect(Collectors.toMap(com.glassvue.domain.catalog.entity.ProductVariant::getId,
                        com.glassvue.domain.catalog.entity.ProductVariant::getProductId));
    }

    /**
     * 재고 부족 옵션 — 관리자 대시보드용 (2026-08-03, 백로그 B-16).
     *
     * <p><b>기준값을 화면이 정하지 않는다.</b> 판정 기준은 {@code catalog.low-stock-threshold} 하나이고,
     * 그 값은 <b>재고 부족 알림({@code StockRunningLowEvent})과 같은 것</b>이다 — 알림은 "3개 남았다"고
     * 하는데 대시보드는 안 세는 식으로 갈리면 어느 쪽을 믿어야 할지 알 수 없다.
     * 그래서 응답에 기준값을 함께 실어 화면 문구까지 서버가 책임진다.
     */
    public LowStockResponse lowStock() {
        long threshold = catalogProperties.lowStockThreshold();
        List<LowStockItemResponse> items =
                variantRepository.findLowStock(threshold, PageRequest.of(0, LOW_STOCK_ITEMS)).stream()
                        .map(LowStockItemResponse::from)
                        .toList();
        return new LowStockResponse(threshold, variantRepository.countLowStock(threshold), items);
    }

    /**
     * 삭제 대기 목록 (관리자 복구 화면, 2026-08-12 F-7).
     *
     * <p>⚠ <b>「언제 사라지나」를 서버가 계산해서 준다.</b> 화면이 {@code deletedAt + 7일} 을 직접
     * 더하면 유예 설정을 바꿨을 때 <b>화면만 낡는다</b> — 재고 부족 대시보드가 임계값을 서버에서
     * 받는 것과 같은 규칙이다.
     */
    public List<DeletedProductResponse> findDeleted() {
        Duration grace = Duration.ofDays(catalogProperties.purgeGraceDays());
        return productRepository.findDeleted().stream()
                .map(p -> DeletedProductResponse.from(p, p.getDeletedAt().plus(grace)))
                .toList();
    }
}
