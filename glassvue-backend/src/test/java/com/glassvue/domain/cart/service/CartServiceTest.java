package com.glassvue.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.glassvue.domain.cart.CartStore;
import com.glassvue.domain.cart.dto.CartResponse;
import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.dto.VariantResponse;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.global.policy.ShippingPolicy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 2026-07-24(C-8): 장바구니가 옵션(variant) 단위가 됐다. cart 는 옵션→상품 매핑(productIdsOfVariants)과
 * 상품 조회(findByIds)를 조합해 재고·가격을 합성한다. 이 테스트도 옵션 기준으로 옮겼다.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartStore cartStore;
    @Mock ProductQueryService productQueryService;
    @Spy ShippingPolicy shippingPolicy = new ShippingPolicy();
    @InjectMocks CartService service;

    private final UUID memberId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();

    /** 옵션 하나짜리 상품 응답 — cart 가 variantId 로 찾아 가격·재고를 읽는다. */
    private ProductResponse product(long price, long stock, ProductStatus status) {
        VariantResponse variant = new VariantResponse(variantId, "기본", 0, price, stock, stock <= 0);
        boolean soldOut = status != ProductStatus.SELLING || stock <= 0;
        return new ProductResponse(productId, "지바", null, "desc", price, null,
                List.of(variant), stock, soldOut, status,
                UUID.randomUUID(), "전자기기", List.of(), 0.0, 0L, 0L, null, null);
    }

    private void stubResolve(long price, long stock, ProductStatus status) {
        when(cartStore.items(memberId)).thenReturn(Map.of(variantId, 2L)); // 기본 수량 2
        when(productQueryService.productIdsOfVariants(any())).thenReturn(Map.of(variantId, productId));
        when(productQueryService.findByIds(any())).thenReturn(List.of(product(price, stock, status)));
    }

    @Test
    @DisplayName("빈 장바구니 → 빈 응답, 상품 조회 안 함")
    void empty() {
        when(cartStore.items(memberId)).thenReturn(Map.of());
        CartResponse res = service.getCart(memberId);
        assertThat(res.items()).isEmpty();
        assertThat(res.totalPrice()).isZero();
        verifyNoInteractions(productQueryService);
    }

    @Test
    @DisplayName("판매중 + 재고 충분 → available=true, 합계 계산")
    void available() {
        stubResolve(10_000, 5, ProductStatus.SELLING);
        CartResponse res = service.getCart(memberId);
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).available()).isTrue();
        assertThat(res.items().get(0).variantId()).isEqualTo(variantId);
        assertThat(res.totalPrice()).isEqualTo(20_000);
        assertThat(res.totalQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("품절(SOLD_OUT) → available=false")
    void soldOut() {
        when(cartStore.items(memberId)).thenReturn(Map.of(variantId, 1L));
        when(productQueryService.productIdsOfVariants(any())).thenReturn(Map.of(variantId, productId));
        when(productQueryService.findByIds(any())).thenReturn(List.of(product(10_000, 10, ProductStatus.SOLD_OUT)));
        CartResponse res = service.getCart(memberId);
        assertThat(res.items().get(0).available()).isFalse();
    }

    @Test
    @DisplayName("옵션 재고보다 많이 담김 → available=false")
    void lowStock() {
        when(cartStore.items(memberId)).thenReturn(Map.of(variantId, 3L));
        when(productQueryService.productIdsOfVariants(any())).thenReturn(Map.of(variantId, productId));
        when(productQueryService.findByIds(any())).thenReturn(List.of(product(10_000, 1, ProductStatus.SELLING)));
        CartResponse res = service.getCart(memberId);
        assertThat(res.items().get(0).available()).isFalse();
    }

    @Test
    @DisplayName("삭제된 옵션은 응답에서 제외 + 장바구니에서 정리")
    void removedVariantCleaned() {
        when(cartStore.items(memberId)).thenReturn(Map.of(variantId, 1L));
        when(productQueryService.productIdsOfVariants(any())).thenReturn(Map.of()); // 옵션이 사라짐
        CartResponse res = service.getCart(memberId);
        assertThat(res.items()).isEmpty();
        verify(cartStore).remove(memberId, variantId);
    }
}
