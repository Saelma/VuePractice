package com.glassvue.domain.catalog.service.query;

import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.dto.ProductSearchCondition;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductRepository productRepository;

    public ProductResponse get(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductResponse.from(product);
    }

    @Cacheable(cacheNames = "products:list", key = "#condition.toString() + '|' + #pageable.toString()")
    public PageResponse<ProductResponse> search(ProductSearchCondition condition, Pageable pageable) {
        return PageResponse.from(productRepository.search(condition, pageable).map(ProductResponse::from));
    }
}
