package com.glassvue.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.glassvue.domain.cart.CartStore;
import com.glassvue.domain.cart.dto.CartResponse;
import com.glassvue.domain.catalog.dto.ProductResponse;
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

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartStore cartStore;
    @Mock ProductQueryService productQueryService;
    // 설정 객체라 목이 아니라 실제 인스턴스를 넣는다 — 배송비 계산은 순수 산술이고,
    // 목으로 두면 항상 0을 돌려줘 "배송비가 안 붙는" 경로만 검증하게 된다.
    @Spy ShippingPolicy shippingPolicy = new ShippingPolicy();
    @InjectMocks CartService service;

    private final UUID memberId = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();

    private ProductResponse product(long price, long stock, ProductStatus status) {
        return new ProductResponse(p1, "지바", "desc", price, null, stock, status,
                UUID.randomUUID(), "전자기기", List.of(), 0.0, 0L, null, null);
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
        when(cartStore.items(memberId)).thenReturn(Map.of(p1, 2L));
        when(productQueryService.findByIds(any())).thenReturn(List.of(product(10_000, 5, ProductStatus.SELLING)));
        CartResponse res = service.getCart(memberId);
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).available()).isTrue();
        assertThat(res.totalPrice()).isEqualTo(20_000);
        assertThat(res.totalQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("품절(SOLD_OUT) → available=false")
    void soldOut() {
        when(cartStore.items(memberId)).thenReturn(Map.of(p1, 1L));
        when(productQueryService.findByIds(any())).thenReturn(List.of(product(10_000, 10, ProductStatus.SOLD_OUT)));
        CartResponse res = service.getCart(memberId);
        assertThat(res.items().get(0).available()).isFalse();
    }

    @Test
    @DisplayName("재고보다 많이 담김 → available=false")
    void lowStock() {
        when(cartStore.items(memberId)).thenReturn(Map.of(p1, 3L));
        when(productQueryService.findByIds(any())).thenReturn(List.of(product(10_000, 1, ProductStatus.SELLING)));
        CartResponse res = service.getCart(memberId);
        assertThat(res.items().get(0).available()).isFalse();
    }

    @Test
    @DisplayName("삭제된 상품은 응답에서 제외 + 장바구니에서 정리")
    void removedProductCleaned() {
        when(cartStore.items(memberId)).thenReturn(Map.of(p1, 1L));
        when(productQueryService.findByIds(any())).thenReturn(List.of()); // 상품이 사라짐
        CartResponse res = service.getCart(memberId);
        assertThat(res.items()).isEmpty();
        verify(cartStore).remove(memberId, p1);
    }
}
