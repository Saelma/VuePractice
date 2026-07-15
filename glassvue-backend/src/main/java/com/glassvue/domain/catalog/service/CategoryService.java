package com.glassvue.domain.catalog.service;

import com.glassvue.domain.catalog.dto.CategoryCreateRequest;
import com.glassvue.domain.catalog.dto.CategoryResponse;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryResponse create(CategoryCreateRequest req) {
        if (categoryRepository.existsByName(req.name())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY);
        }
        Category category = categoryRepository.save(Category.builder().name(req.name()).build());
        return CategoryResponse.from(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
