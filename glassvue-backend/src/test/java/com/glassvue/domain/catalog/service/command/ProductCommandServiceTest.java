package com.glassvue.domain.catalog.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.catalog.config.CatalogProperties;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.event.StockRunningLowEvent;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.entity.StockChangeReason;
import com.glassvue.domain.catalog.entity.StockHistory;
import com.glassvue.domain.catalog.repository.ProductVariantRepository;
import com.glassvue.domain.catalog.repository.StockHistoryRepository;
import com.glassvue.domain.catalog.repository.VariantStockSnapshot;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 2026-07-24(C-8): 재고가 옵션(variant)으로 내려가면서 재고 차감·복원·저재고 이벤트가
 * variantRepository 기준으로 바뀌었다. 이 테스트도 옵션 단위로 옮겼다.
 */
@ExtendWith(MockitoExtension.class)
class ProductCommandServiceTest {

    @Mock ProductRepository productRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock StockHistoryRepository stockHistoryRepository;
    @Mock ImageService imageService;
    @Mock ApplicationEventPublisher eventPublisher;

    private final CatalogProperties catalogProperties = new CatalogProperties(5); // 임계치 5
    private ProductCommandService service;

    private final UUID productId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private Product productWithImageGroup(UUID groupId) {
        return Product.builder()
                .name("지바").description("d").price(10_000)
                .status(ProductStatus.SELLING).imageGroupId(groupId)
                .category(Category.builder().name("키보드").build())
                .build();
    }

    private VariantStockSnapshot snap(long stock) {
        return new VariantStockSnapshot(productId, "무선키보드", "검정/M", stock);
    }

    @BeforeEach
    void setUp() {
        service = new ProductCommandService(
                productRepository, variantRepository, categoryRepository, stockHistoryRepository,
                imageService, catalogProperties, eventPublisher);
    }

    private static void assertErrorCode(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("옵션 재고 차감: 원자적 UPDATE가 0행이면 OUT_OF_STOCK")
    void decreaseStock_outOfStock() {
        when(variantRepository.decreaseStock(variantId, 5)).thenReturn(0);
        assertErrorCode(() -> service.decreaseStock(variantId, 5, orderId), ErrorCode.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("옵션 재고 차감: 1행 변경이면 정상")
    void decreaseStock_ok() {
        when(variantRepository.decreaseStock(variantId, 2)).thenReturn(1);
        when(variantRepository.findStockSnapshot(variantId)).thenReturn(Optional.of(snap(48)));
        assertThatCode(() -> service.decreaseStock(variantId, 2, orderId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("잔여가 임계치 초과면 저재고 이벤트를 발행하지 않는다")
    void decreaseStock_aboveThreshold_noEvent() {
        when(variantRepository.decreaseStock(variantId, 1)).thenReturn(1);
        when(variantRepository.findStockSnapshot(variantId)).thenReturn(Optional.of(snap(6)));
        service.decreaseStock(variantId, 1, orderId);
        verify(eventPublisher, never()).publishEvent(any(StockRunningLowEvent.class));
    }

    @Test
    @DisplayName("잔여가 임계치 이하면 StockRunningLowEvent 발행(옵션명 포함)")
    void decreaseStock_atThreshold_publishesEvent() {
        when(variantRepository.decreaseStock(variantId, 1)).thenReturn(1);
        when(variantRepository.findStockSnapshot(variantId)).thenReturn(Optional.of(snap(5)));
        service.decreaseStock(variantId, 1, orderId);
        verify(eventPublisher).publishEvent(new StockRunningLowEvent(productId, "무선키보드", "검정/M", 5, 5));
    }

    @Test
    @DisplayName("품절(0)도 저재고 이벤트에 포함")
    void decreaseStock_soldOut_publishesEvent() {
        when(variantRepository.decreaseStock(variantId, 3)).thenReturn(1);
        when(variantRepository.findStockSnapshot(variantId)).thenReturn(Optional.of(snap(0)));
        service.decreaseStock(variantId, 3, orderId);
        verify(eventPublisher).publishEvent(new StockRunningLowEvent(productId, "무선키보드", "검정/M", 0, 5));
    }

    @Test
    @DisplayName("차감 실패(OUT_OF_STOCK) 시엔 이벤트를 발행하지 않는다")
    void decreaseStock_failure_noEvent() {
        when(variantRepository.decreaseStock(variantId, 5)).thenReturn(0);
        assertErrorCode(() -> service.decreaseStock(variantId, 5, orderId), ErrorCode.OUT_OF_STOCK);
        verify(eventPublisher, never()).publishEvent(any(StockRunningLowEvent.class));
    }

    @Test
    @DisplayName("삭제: 없는 상품 → PRODUCT_NOT_FOUND, 삭제·이미지 정리 안 함")
    void delete_notFound() {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());
        assertErrorCode(() -> service.delete(productId), ErrorCode.PRODUCT_NOT_FOUND);
        verify(productRepository, never()).delete(any());
        verify(imageService, never()).deleteGroup(any());
    }

    @Test
    @DisplayName("삭제: 상품과 함께 이미지 그룹도 정리한다(옵션은 FK CASCADE)")
    void delete_ok() {
        UUID groupId = UUID.randomUUID();
        Product product = productWithImageGroup(groupId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        service.delete(productId);
        verify(productRepository).delete(product);
        verify(imageService).deleteGroup(groupId);
    }

    @Test
    @DisplayName("재고 복원은 옵션 리포지토리에 위임")
    void increaseStock() {
        service.increaseStock(variantId, 3, StockChangeReason.CANCEL, orderId);
        verify(variantRepository).increaseStock(variantId, 3);
    }

    @Test
    @DisplayName("재고 복원: variantId가 null이면 아무것도 안 한다(옛 데이터 방어)")
    void increaseStock_nullVariant() {
        service.increaseStock(null, 3, StockChangeReason.CANCEL, orderId);
        verify(variantRepository, never()).increaseStock(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    // ── 재고 이력 (2026-08-04, B-19) ────────────────────────────
    //
    // 이력은 **재고가 실제로 변했을 때만** 남아야 한다 — 안 변했는데 남으면 원장이 거짓이 된다.
    // 여기서는 그 조건과 부호·스냅샷을 본다(옵션명으로 잇는 것은 통합 테스트에서).

    private StockHistory captureHistory() {
        ArgumentCaptor<StockHistory> captor = ArgumentCaptor.forClass(StockHistory.class);
        verify(stockHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("주문 차감: 이력을 **음수**로 남기고 차감 직후 재고·주문 id 를 함께 적는다")
    void decreaseStock_recordsNegativeHistory() {
        when(variantRepository.decreaseStock(variantId, 2)).thenReturn(1);
        when(variantRepository.findStockSnapshot(variantId)).thenReturn(Optional.of(snap(48)));

        service.decreaseStock(variantId, 2, orderId);

        StockHistory h = captureHistory();
        assertThat(h.getReason()).isEqualTo(StockChangeReason.ORDER);
        assertThat(h.getQuantity()).isEqualTo(-2); // 부호는 호출부가 아니라 팩토리가 붙인다
        assertThat(h.getStockAfter()).isEqualTo(48);
        assertThat(h.getProductId()).isEqualTo(productId);
        assertThat(h.getVariantName()).isEqualTo("검정/M");
        assertThat(h.getVariantId()).isEqualTo(variantId);
        assertThat(h.getOrderId()).isEqualTo(orderId);
        // 주문 경로는 행위자를 안 적는다 — orderId 로 되짚을 수 있어 같은 정보를 두 번 적지 않는다.
        assertThat(h.getActorId()).isNull();
        assertThat(h.getActorName()).isNull();
    }

    @Test
    @DisplayName("주문 차감 실패(OUT_OF_STOCK): 재고가 안 변했으므로 이력도 안 남는다")
    void decreaseStock_failure_noHistory() {
        when(variantRepository.decreaseStock(variantId, 5)).thenReturn(0);
        assertErrorCode(() -> service.decreaseStock(variantId, 5, orderId), ErrorCode.OUT_OF_STOCK);
        verify(stockHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("복원: 이력을 **양수**로 남기고 취소/반품을 사유로 구분한다")
    void increaseStock_recordsPositiveHistory() {
        when(variantRepository.increaseStock(variantId, 3)).thenReturn(1);
        when(variantRepository.findStockSnapshot(variantId)).thenReturn(Optional.of(snap(9)));

        service.increaseStock(variantId, 3, StockChangeReason.RETURN, orderId);

        StockHistory h = captureHistory();
        assertThat(h.getReason()).isEqualTo(StockChangeReason.RETURN);
        assertThat(h.getQuantity()).isEqualTo(3);
        assertThat(h.getStockAfter()).isEqualTo(9);
        assertThat(h.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("복원: 옵션이 이미 삭제됐으면(0행) 재고가 안 변했으므로 이력도 안 남는다")
    void increaseStock_deletedVariant_noHistory() {
        when(variantRepository.increaseStock(variantId, 3)).thenReturn(0);

        service.increaseStock(variantId, 3, StockChangeReason.CANCEL, orderId);

        verify(stockHistoryRepository, never()).save(any());
        // 스냅샷도 읽지 않는다 — 복원 대상이 없으면 더 볼 것이 없다.
        verify(variantRepository, never()).findStockSnapshot(any());
    }

    @Test
    @DisplayName("복원: variantId 가 null 이면 이력도 안 남는다")
    void increaseStock_nullVariant_noHistory() {
        service.increaseStock(null, 3, StockChangeReason.CANCEL, orderId);
        verify(stockHistoryRepository, never()).save(any());
    }
}
