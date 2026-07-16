package com.glassvue.domain.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.glassvue.domain.catalog.dto.CategoryCreateRequest;
import com.glassvue.domain.catalog.dto.CategoryResponse;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @InjectMocks CategoryService service;

    @Test
    @DisplayName("생성: 이름 중복 → DUPLICATE_CATEGORY, 저장 안 함")
    void create_duplicate() {
        when(categoryRepository.existsByName("전자기기")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CategoryCreateRequest("전자기기")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_CATEGORY);
        org.mockito.Mockito.verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("생성: 새 이름 → 저장 후 응답 반환")
    void create_ok() {
        when(categoryRepository.existsByName("패션")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        CategoryResponse res = service.create(new CategoryCreateRequest("패션"));
        assertThat(res.name()).isEqualTo("패션");
    }
}
