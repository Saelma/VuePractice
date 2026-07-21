package com.glassvue.domain.catalog.service;

import com.glassvue.domain.catalog.dto.CategoryCreateRequest;
import com.glassvue.domain.catalog.dto.CategoryResponse;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryResponse create(CategoryCreateRequest req) {
        if (categoryRepository.existsByName(req.name())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY);
        }
        Category category = categoryRepository.save(Category.builder().name(req.name()).build());
        return CategoryResponse.from(category);
    }

    /**
     * 카테고리 삭제. 소속 상품이 하나라도 있으면 막는다(CATEGORY_IN_USE).
     * product.category_id가 nullable=false FK라 상품이 있으면 어차피 DB가 막지만,
     * 여기서 먼저 걸러 의미 있는 409를 돌려준다(FK 위반 500 대신).
     */
    public void delete(UUID id) {
        if (!categoryRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        if (productRepository.existsByCategoryId(id)) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE);
        }
        categoryRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
