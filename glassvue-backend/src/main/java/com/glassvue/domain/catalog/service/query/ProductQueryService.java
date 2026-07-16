package com.glassvue.domain.catalog.service.query;

import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.dto.ProductSearchCondition;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.repository.ProductRepository;
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
    private final ImageService imageService;

    public ProductResponse get(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductResponse.from(product, imageService.findByGroup(product.getImageGroupId()));
    }

    /** 상품 존재 확인 (리뷰·문의 등 타 도메인이 상품에 종속 리소스를 만들 때). 없으면 PRODUCT_NOT_FOUND. */
    public void ensureExists(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    /** 여러 상품을 한 번에 조회 (장바구니 등 타 도메인용). 이미지는 포함하지 않는다. */
    public List<ProductResponse> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return productRepository.findAllById(ids).stream().map(ProductResponse::from).toList();
    }

    @Cacheable(cacheNames = "products:list", key = "#condition.toString() + '|' + #pageable.toString()")
    public PageResponse<ProductResponse> search(ProductSearchCondition condition, Pageable pageable) {
        Page<Product> page = productRepository.search(condition, pageable);

        // 페이지 상품들의 이미지 그룹을 한 번에 조회 (N+1 회피)
        List<UUID> groupIds = page.getContent().stream()
                .map(Product::getImageGroupId).filter(Objects::nonNull).toList();
        Map<UUID, List<ImageResponse>> imagesByGroup = imageService.findByGroups(groupIds);

        Page<ProductResponse> mapped = page.map(p -> ProductResponse.from(
                p,
                p.getImageGroupId() == null
                        ? List.of()
                        : imagesByGroup.getOrDefault(p.getImageGroupId(), List.of())));
        return PageResponse.from(mapped);
    }
}
