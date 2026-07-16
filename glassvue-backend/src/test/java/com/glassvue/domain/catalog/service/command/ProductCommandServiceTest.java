package com.glassvue.domain.catalog.service.command;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.image.service.ImageService;
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
class ProductCommandServiceTest {

    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ImageService imageService;
    @InjectMocks ProductCommandService service;

    private final UUID productId = UUID.randomUUID();

    private static void assertErrorCode(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("재고 차감: 원자적 UPDATE가 0행이면 OUT_OF_STOCK")
    void decreaseStock_outOfStock() {
        when(productRepository.decreaseStock(productId, 5)).thenReturn(0);
        assertErrorCode(() -> service.decreaseStock(productId, 5), ErrorCode.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("재고 차감: 1행 변경이면 정상")
    void decreaseStock_ok() {
        when(productRepository.decreaseStock(productId, 2)).thenReturn(1);
        assertThatCode(() -> service.decreaseStock(productId, 2)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("삭제: 없는 상품 → PRODUCT_NOT_FOUND, deleteById 호출 안 함")
    void delete_notFound() {
        when(productRepository.existsById(productId)).thenReturn(false);
        assertErrorCode(() -> service.delete(productId), ErrorCode.PRODUCT_NOT_FOUND);
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("삭제: 존재하면 deleteById 호출")
    void delete_ok() {
        when(productRepository.existsById(productId)).thenReturn(true);
        service.delete(productId);
        verify(productRepository).deleteById(productId);
    }

    @Test
    @DisplayName("재고 복원은 리포지토리에 위임")
    void increaseStock() {
        service.increaseStock(productId, 3);
        verify(productRepository).increaseStock(productId, 3);
    }
}
