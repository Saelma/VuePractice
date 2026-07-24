package com.glassvue.domain.catalog.service.query;

import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.dto.ProductSearchCondition;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductVariant;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.repository.ProductVariantRepository;
import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.response.PageResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ImageService imageService;

    public ProductResponse get(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        List<ProductVariant> variants =
                variantRepository.findByProductIdOrderBySortOrderAscCreatedAtAsc(id);
        return ProductResponse.from(product, variants, imageService.findByGroup(product.getImageGroupId()));
    }

    /** 상품 존재 확인 (리뷰·문의 등 타 도메인이 상품에 종속 리소스를 만들 때). 없으면 PRODUCT_NOT_FOUND. */
    public void ensureExists(UUID id) {
        if (!productRepository.existsById(id)) {
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
        return products.stream()
                .map(p -> ProductResponse.from(p,
                        variantsByProduct.getOrDefault(p.getId(), List.of()),
                        imagesFor(p, imagesByGroup)))
                .toList();
    }

    @Cacheable(cacheNames = "products:list", key = "#condition.toString() + '|' + #pageable.toString()")
    public PageResponse<ProductResponse> search(ProductSearchCondition condition, Pageable pageable) {
        Page<Product> page = productRepository.search(condition, pageable);
        List<Product> products = page.getContent();
        Map<UUID, List<ProductVariant>> variantsByProduct = variantsOf(products);
        Map<UUID, List<ImageResponse>> imagesByGroup = imagesOf(products);

        Page<ProductResponse> mapped = page.map(p -> ProductResponse.from(p,
                variantsByProduct.getOrDefault(p.getId(), List.of()),
                imagesFor(p, imagesByGroup)));
        return PageResponse.from(mapped);
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
}
