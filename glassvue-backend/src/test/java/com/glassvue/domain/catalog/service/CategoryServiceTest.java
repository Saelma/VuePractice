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
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @Mock ProductRepository productRepository;
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

    @Test
    @DisplayName("삭제: 없는 카테고리 → CATEGORY_NOT_FOUND")
    void delete_notFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.existsById(id)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
        org.mockito.Mockito.verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("삭제: 소속 상품 있음 → CATEGORY_IN_USE, 삭제 안 함")
    void delete_inUse() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.existsById(id)).thenReturn(true);
        when(productRepository.existsByCategoryId(id)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_IN_USE);
        org.mockito.Mockito.verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("삭제: 빈 카테고리 → 삭제")
    void delete_ok() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.existsById(id)).thenReturn(true);
        when(productRepository.existsByCategoryId(id)).thenReturn(false);
        service.delete(id);
        org.mockito.Mockito.verify(categoryRepository).deleteById(id);
    }
}
