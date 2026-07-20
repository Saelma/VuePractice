package com.glassvue.domain.catalog.service.command;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.catalog.config.CatalogProperties;
import com.glassvue.domain.catalog.event.StockRunningLowEvent;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.repository.StockSnapshot;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProductCommandServiceTest {

    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ImageService imageService;
    @Mock ApplicationEventPublisher eventPublisher;

    // record라 목킹 대신 실제 값 사용 — 임계치 5
    private final CatalogProperties catalogProperties = new CatalogProperties(5);
    private ProductCommandService service;

    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProductCommandService(
                productRepository, categoryRepository, imageService, catalogProperties, eventPublisher);
    }

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
        when(productRepository.findStockSnapshot(productId))
                .thenReturn(Optional.of(new StockSnapshot("무선키보드", 48)));
        assertThatCode(() -> service.decreaseStock(productId, 2)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("재고 차감: 잔여재고가 임계치 초과면 재고 부족 이벤트를 발행하지 않는다")
    void decreaseStock_aboveThreshold_noEvent() {
        when(productRepository.decreaseStock(productId, 1)).thenReturn(1);
        when(productRepository.findStockSnapshot(productId))
                .thenReturn(Optional.of(new StockSnapshot("무선키보드", 6)));

        service.decreaseStock(productId, 1);

        verify(eventPublisher, never()).publishEvent(any(StockRunningLowEvent.class));
    }

    @Test
    @DisplayName("재고 차감: 잔여재고가 임계치 이하면 StockRunningLowEvent 발행")
    void decreaseStock_atThreshold_publishesEvent() {
        when(productRepository.decreaseStock(productId, 1)).thenReturn(1);
        when(productRepository.findStockSnapshot(productId))
                .thenReturn(Optional.of(new StockSnapshot("무선키보드", 5)));

        service.decreaseStock(productId, 1);

        verify(eventPublisher).publishEvent(new StockRunningLowEvent(productId, "무선키보드", 5, 5));
    }

    @Test
    @DisplayName("재고 차감: 품절(0)도 재고 부족 이벤트에 포함")
    void decreaseStock_soldOut_publishesEvent() {
        when(productRepository.decreaseStock(productId, 3)).thenReturn(1);
        when(productRepository.findStockSnapshot(productId))
                .thenReturn(Optional.of(new StockSnapshot("무선키보드", 0)));

        service.decreaseStock(productId, 3);

        verify(eventPublisher).publishEvent(new StockRunningLowEvent(productId, "무선키보드", 0, 5));
    }

    @Test
    @DisplayName("재고 차감 실패(OUT_OF_STOCK) 시에는 이벤트를 발행하지 않는다")
    void decreaseStock_failure_noEvent() {
        when(productRepository.decreaseStock(productId, 5)).thenReturn(0);
        assertErrorCode(() -> service.decreaseStock(productId, 5), ErrorCode.OUT_OF_STOCK);
        verify(eventPublisher, never()).publishEvent(any(StockRunningLowEvent.class));
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
