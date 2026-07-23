package com.glassvue.domain.catalog.service.command;

import com.glassvue.domain.catalog.config.CatalogProperties;
import com.glassvue.domain.catalog.dto.ProductCreateRequest;
import com.glassvue.domain.catalog.dto.ProductUpdateRequest;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.event.StockRunningLowEvent;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 조작(관리자). 목록 캐시는 변경 시 무효화. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ImageService imageService;
    private final CatalogProperties catalogProperties;
    private final ApplicationEventPublisher eventPublisher;

    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public UUID create(ProductCreateRequest req) {
        Category category = findCategory(req.categoryId());
        UUID imageGroupId = imageService.createGroup(req.imageIds());
        Product product = Product.builder()
                .name(req.name())
                .description(req.description())
                .price(req.price())
                .listPrice(req.listPrice())
                .stock(req.stock())
                .status(req.status())
                .imageGroupId(imageGroupId)
                .category(category)
                .build();
        Product saved = productRepository.save(product);
        log.info("Product created: {}", saved.getId());
        return saved.getId();
    }

    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void update(UUID id, ProductUpdateRequest req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Category category = findCategory(req.categoryId());
        UUID oldGroupId = product.getImageGroupId();
        UUID imageGroupId = imageService.createGroup(req.imageIds()); // 새 그룹으로 교체(간단화)
        product.update(req.name(), req.description(), req.price(), req.listPrice(),
                req.stock(), req.status(), imageGroupId, category);
        // createGroup이 유지할 이미지를 새 그룹으로 옮긴 뒤라, 옛 그룹엔 사용자가 뺀 이미지만 남는다.
        // 순서를 바꾸면 유지하려던 이미지까지 지워진다.
        imageService.deleteGroup(oldGroupId);
    }

    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void delete(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        UUID imageGroupId = product.getImageGroupId();
        productRepository.delete(product);
        imageService.deleteGroup(imageGroupId); // 상품이 사라지면 그 이미지도 주인이 없다
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    /** 주문용 재고 차감(원자적). 재고 부족이면 예외. (호출부는 상품 존재를 이미 검증한 상태) */
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void decreaseStock(UUID productId, long quantity) {
        if (productRepository.decreaseStock(productId, quantity) == 0) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        publishIfRunningLow(productId);
    }

    /**
     * 차감 후 잔여재고가 임계치 이하면 재고 부족 이벤트 발행 — 구독자(알림 등)는 catalog가 모른다.
     * 재고는 catalog 소유이므로 주문이 아니라 이곳이 발행 주체다.
     */
    private void publishIfRunningLow(UUID productId) {
        productRepository.findStockSnapshot(productId)
                .filter(s -> s.stock() <= catalogProperties.lowStockThreshold())
                .ifPresent(s -> eventPublisher.publishEvent(new StockRunningLowEvent(
                        productId, s.name(), s.stock(), catalogProperties.lowStockThreshold())));
    }

    /** 주문 취소 시 재고 복원. (상품이 이미 삭제됐으면 조용히 무시) */
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void increaseStock(UUID productId, long quantity) {
        productRepository.increaseStock(productId, quantity);
    }
}
