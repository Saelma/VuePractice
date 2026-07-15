package com.glassvue.domain.catalog.service.command;

import com.glassvue.domain.catalog.dto.ProductCreateRequest;
import com.glassvue.domain.catalog.dto.ProductUpdateRequest;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
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

    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public UUID create(ProductCreateRequest req) {
        Category category = findCategory(req.categoryId());
        Product product = Product.builder()
                .name(req.name())
                .description(req.description())
                .price(req.price())
                .stock(req.stock())
                .status(req.status())
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
        product.update(req.name(), req.description(), req.price(), req.stock(), req.status(), category);
    }

    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void delete(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        productRepository.deleteById(id);
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }
}
