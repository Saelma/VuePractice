package com.glassvue.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.glassvue.domain.cart.CartStore;
import com.glassvue.domain.cart.dto.CartResponse;
import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.dto.VariantResponse;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.point.entity.MemberGrade;
import com.glassvue.domain.point.service.PointService;
import com.glassvue.global.policy.ShippingPolicy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
    /** 등급별 무료배송 기준에만 쓴다(2026-08-28, G-6) — cart 는 적립금 잔액을 안 만진다. */
    @Mock PointService pointService;
    @InjectMocks CartService service;

    @BeforeEach
    void defaultGrade() {
        // 등급을 말하지 않는 테스트는 전부 BRONZE(기본 기준 30,000원)로 읽는다 —
        // 그래야 G-6 이전에 쓰인 배송비 단언들이 «그대로 참» 인지 이 파일에서 보인다.
        lenient().when(pointService.gradeOf(any())).thenReturn(MemberGrade.BRONZE);
    }

    private final UUID memberId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();

    /** 옵션 하나짜리 상품 응답 — cart 가 variantId 로 찾아 가격·재고를 읽는다. */
    private ProductResponse product(long price, long stock, ProductStatus status) {
        return product(price, stock, status, false);
    }

    /** {@code deleted} 는 「삭제 대기」다(2026-08-12, F-7) — 줄은 남고 구매만 막힌다. */
    private ProductResponse product(long price, long stock, ProductStatus status, boolean deleted) {
        // ⚠ 세일 없는 상태다 — price == regularPrice, discountRate == null (2026-08-19, G-5).
        VariantResponse variant = new VariantResponse(variantId, "기본", 0, price, price, stock, stock <= 0);
        boolean soldOut = status != ProductStatus.SELLING || stock <= 0;
        return new ProductResponse(productId, "지바", null, "desc", price, price, null, null, null,
                List.of(variant), stock, soldOut, status,
                UUID.randomUUID(), "전자기기", List.of(), 0.0, 0L, 0L, deleted, null, null);
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

    /**
     * 🔴 삭제 대기 상품 (2026-08-12, F-7) — <b>줄이 사라지지 않는 것</b>이 요점이다.
     *
     * <p>못 찾은 줄은 이 서비스가 그 자리에서 정리하는데(위 {@code cartStore.remove}),
     * 삭제 대기는 <b>그 경로로 가면 안 된다</b>: 지워 버리면 상품을 복구해도 장바구니는 안 돌아온다.
     * 그래서 {@code findByIds} 가 대기 상품도 돌려주고, 여기서 <b>available 로만</b> 막는다.
     */
    @Test
    @DisplayName("🔴 삭제 대기 상품 → 줄은 남고 available=false (지우면 복구해도 안 돌아온다)")
    void deletedProduct_lineRemainsButUnavailable() {
        when(cartStore.items(memberId)).thenReturn(Map.of(variantId, 1L));
        when(productQueryService.productIdsOfVariants(any())).thenReturn(Map.of(variantId, productId));
        when(productQueryService.findByIds(any()))
                .thenReturn(List.of(product(10_000, 10, ProductStatus.SELLING, true)));

        CartResponse res = service.getCart(memberId);

        assertThat(res.items()).as("줄이 사라지면 복구가 반쪽이 된다").hasSize(1);
        assertThat(res.items().get(0).available()).isFalse();
        verify(cartStore, never()).remove(any(), any());
    }

    @Test
    @DisplayName("⚠ 대조군: 대기 중이 아니면 그대로 살 수 있다(플래그가 늘 참이면 막는 의미가 없다)")
    void aliveProduct_stillAvailable() {
        when(cartStore.items(memberId)).thenReturn(Map.of(variantId, 1L));
        when(productQueryService.productIdsOfVariants(any())).thenReturn(Map.of(variantId, productId));
        when(productQueryService.findByIds(any()))
                .thenReturn(List.of(product(10_000, 10, ProductStatus.SELLING, false)));

        assertThat(service.getCart(memberId).items().get(0).available()).isTrue();
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

    // ─────────── 등급별 무료배송 (2026-08-28, BACKLOG G-6) ───────────
    //
    // 기본 기준은 30,000원(ShippingPolicy 기본값)이고 담긴 금액은 12,000 × 2 = 24,000원이다.
    // 즉 **같은 장바구니가 등급에 따라 배송비가 갈리는** 자리를 고른 것이다.

    @Test
    @DisplayName("BRONZE — 24,000원은 기준(30,000) 미만이라 배송비가 붙는다")
    void bronzePaysShipping() {
        when(pointService.gradeOf(memberId)).thenReturn(MemberGrade.BRONZE);
        stubResolve(12_000, 5, ProductStatus.SELLING);

        CartResponse res = service.getCart(memberId);

        assertThat(res.totalPrice()).isEqualTo(24_000);
        assertThat(res.shippingFee()).isEqualTo(3_000);
        assertThat(res.payAmount()).isEqualTo(27_000);
        assertThat(res.amountUntilFree()).isEqualTo(6_000);   // 30,000 − 24,000
    }

    @Test
    @DisplayName("🔴 SILVER — 같은 24,000원인데 기준이 24,000으로 내려와 무료가 된다")
    void silverGetsFreeShipping() {
        when(pointService.gradeOf(memberId)).thenReturn(MemberGrade.SILVER);
        stubResolve(12_000, 5, ProductStatus.SELLING);

        CartResponse res = service.getCart(memberId);

        assertThat(res.shippingFee()).isZero();              // 경계값: 24,000 «이상»
        assertThat(res.payAmount()).isEqualTo(24_000);
        // 🔴 여기가 G-6 에서 가장 틀리기 쉬운 자리다 — feeFor 만 등급을 쓰고 amountUntilFree 가
        //    설정값(30,000)을 그대로 보면 «6,000원 더 담으면 무료배송» 이라 말하면서 이미 무료다.
        assertThat(res.amountUntilFree()).isZero();
    }

    @Test
    @DisplayName("VIP — 기준이 12,000까지 내려간다")
    void vipThresholdIsLowest() {
        when(pointService.gradeOf(memberId)).thenReturn(MemberGrade.VIP);
        stubResolve(5_000, 5, ProductStatus.SELLING);        // 10,000원

        CartResponse res = service.getCart(memberId);

        assertThat(res.shippingFee()).isEqualTo(3_000);      // 12,000 미만이라 아직 붙는다
        assertThat(res.amountUntilFree()).isEqualTo(2_000);  // 12,000 − 10,000
    }
}
