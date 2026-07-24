package com.glassvue.domain.catalog.service.command;

import com.glassvue.domain.catalog.config.CatalogProperties;
import com.glassvue.domain.catalog.dto.ProductCreateRequest;
import com.glassvue.domain.catalog.dto.ProductUpdateRequest;
import com.glassvue.domain.catalog.dto.VariantRequest;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductVariant;
import com.glassvue.domain.catalog.event.StockRunningLowEvent;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.repository.ProductVariantRepository;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 조작(관리자). 목록 캐시는 변경 시 무효화.
 *
 * <p>2026-07-24(C-8): 재고가 옵션(variant)으로 내려갔다. 상품을 만들 때 <b>옵션도 함께</b> 만들고,
 * 재고 차감·복원은 상품이 아니라 <b>옵션 단위</b>로 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
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
                .status(req.status())
                .imageGroupId(imageGroupId)
                .category(category)
                .build();
        Product saved = productRepository.save(product);
        saveVariants(saved.getId(), req.variants());
        log.info("Product created: {} ({} variants)", saved.getId(),
                req.variants() == null ? 0 : req.variants().size());
        return saved.getId();
    }

    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void update(UUID id, ProductUpdateRequest req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Category category = findCategory(req.categoryId());
        UUID oldGroupId = product.getImageGroupId();
        UUID imageGroupId = imageService.createGroup(req.imageIds());
        product.update(req.name(), req.description(), req.price(), req.listPrice(),
                req.status(), imageGroupId, category);

        // 옵션은 통째로 교체한다(delete-all + insert). 관리 화면이 옵션 전체를 다시 보내므로
        // 부분 갱신(id 매칭·삭제 판정)보다 통째 교체가 단순하고 어긋날 여지가 없다.
        // ⚠ 이미 주문된 옵션을 지워도 order_item 은 스냅샷(variant_name)을 갖고 있어 과거 주문 표시는 멀쩡하다.
        //    재고 복원 대상 variant_id 는 사라질 수 있지만 increaseStock 이 0행이면 조용히 무시한다.
        variantRepository.deleteAll(variantRepository.findByProductIdOrderBySortOrderAscCreatedAtAsc(id));
        variantRepository.flush(); // 삭제를 먼저 DB 에 보내 새 옵션과 섞이지 않게
        saveVariants(id, req.variants());

        imageService.deleteGroup(oldGroupId);
    }

    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void delete(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        UUID imageGroupId = product.getImageGroupId();
        productRepository.delete(product); // FK ON DELETE CASCADE 로 옵션도 함께 지워진다(V22)
        imageService.deleteGroup(imageGroupId);
    }

    /** 옵션 목록을 정렬 순서대로 저장한다. 비어 있으면 상품이 주문 불가가 되므로 최소 1개를 요구한다. */
    private void saveVariants(UUID productId, List<VariantRequest> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_NO_VARIANT);
        }
        int order = 0;
        for (VariantRequest v : variants) {
            variantRepository.save(ProductVariant.of(
                    productId, v.name().trim(), v.priceDelta(), v.stock(), order++));
        }
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    /**
     * 주문용 재고 차감(원자적) — 옵션 단위. 재고 부족이면 예외.
     * 예전 decreaseStock(productId)가 재고를 옵션으로 옮기며 variantId를 받게 됐다.
     */
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void decreaseStock(UUID variantId, long quantity) {
        if (variantRepository.decreaseStock(variantId, quantity) == 0) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        publishIfRunningLow(variantId);
    }

    /** 차감 후 잔여가 임계치 이하면 재고 부족 이벤트 발행(어느 옵션인지 함께). */
    private void publishIfRunningLow(UUID variantId) {
        variantRepository.findStockSnapshot(variantId)
                .filter(s -> s.stock() <= catalogProperties.lowStockThreshold())
                .ifPresent(s -> eventPublisher.publishEvent(new StockRunningLowEvent(
                        s.productId(), s.productName(), s.variantName(),
                        s.stock(), catalogProperties.lowStockThreshold())));
    }

    /** 주문 취소 시 재고 복원 — 옵션 단위. 옵션이 삭제됐거나 정보가 없으면 조용히 무시. */
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void increaseStock(UUID variantId, long quantity) {
        if (variantId == null) {
            return;
        }
        variantRepository.increaseStock(variantId, quantity);
    }
}
