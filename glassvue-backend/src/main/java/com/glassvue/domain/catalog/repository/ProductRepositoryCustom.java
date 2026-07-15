package com.glassvue.domain.catalog.repository;

import com.glassvue.domain.catalog.dto.ProductSearchCondition;
import com.glassvue.domain.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepositoryCustom {
    Page<Product> search(ProductSearchCondition condition, Pageable pageable);
}
