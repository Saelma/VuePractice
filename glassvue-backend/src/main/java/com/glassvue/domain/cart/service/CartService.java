package com.glassvue.domain.cart.service;

import com.glassvue.domain.cart.CartStore;
import com.glassvue.domain.cart.dto.CartItemAddRequest;
import com.glassvue.domain.cart.dto.CartItemResponse;
import com.glassvue.domain.cart.dto.CartResponse;
import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.dto.VariantResponse;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.policy.ShippingPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장바구니 (2026-07-24 C-8: 옵션 단위).
 *
 * <p>담기는 단위가 상품에서 <b>옵션(variant)</b>으로 바뀌었다. 상태는 Redis(CartStore, field=variantId),
 * 상품·옵션 정보는 catalog 공개 서비스에서 합성한다. cart 는 catalog 리포지토리를 직접 만지지 않고
 * {@code productIdsOfVariants}(옵션→상품) + {@code findByIds}(상품+옵션+이미지) 두 공개 API 만 쓴다.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartStore cartStore;
    private final ProductQueryService productQueryService;
    private final ShippingPolicy shippingPolicy;

    /** 옵션 존재 확인 후 담기(수량 증가). 없는 옵션이면 VARIANT_NOT_FOUND. */
    public void add(UUID memberId, CartItemAddRequest req) {
        if (!productQueryService.productIdsOfVariants(List.of(req.variantId())).containsKey(req.variantId())) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND);
        }
        cartStore.add(memberId, req.variantId(), req.quantity());
    }

    public void setQuantity(UUID memberId, UUID variantId, long quantity) {
        if (!productQueryService.productIdsOfVariants(List.of(variantId)).containsKey(variantId)) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND);
        }
        cartStore.set(memberId, variantId, quantity);
    }

    public void remove(UUID memberId, UUID variantId) {
        cartStore.remove(memberId, variantId);
    }

    public void clear(UUID memberId) {
        cartStore.clear(memberId);
    }

    public CartResponse getCart(UUID memberId) {
        Map<UUID, Long> items = cartStore.items(memberId); // variantId -> qty
        if (items.isEmpty()) {
            return new CartResponse(List.of(), 0, 0, 0, 0, 0);
        }

        // 옵션 → 상품 매핑, 그리고 그 상품들의 정보(옵션·이미지 포함)를 한 번에.
        Map<UUID, UUID> productIdByVariant = productQueryService.productIdsOfVariants(items.keySet());
        Map<UUID, ProductResponse> products = productQueryService
                .findByIds(productIdByVariant.values().stream().distinct().toList()).stream()
                .collect(Collectors.toMap(ProductResponse::id, Function.identity()));

        List<CartItemResponse> lines = new ArrayList<>();
        long totalQuantity = 0;
        long totalPrice = 0;
        for (Map.Entry<UUID, Long> e : items.entrySet()) {
            UUID variantId = e.getKey();
            UUID productId = productIdByVariant.get(variantId);
            ProductResponse product = productId == null ? null : products.get(productId);
            if (product == null) {
                cartStore.remove(memberId, variantId); // 삭제된 옵션/상품은 정리
                continue;
            }
            VariantResponse variant = product.variants().stream()
                    .filter(v -> v.id().equals(variantId)).findFirst().orElse(null);
            if (variant == null) {
                cartStore.remove(memberId, variantId);
                continue;
            }

            long qty = e.getValue();
            long lineTotal = variant.price() * qty;
            // 🔴 삭제 대기 상품은 **줄을 지우지 않고 구매만 막는다**(2026-08-12, F-7, 사용자 결정).
            //    지워 버리면 상품을 복구해도 장바구니는 안 돌아온다 — 유예를 둔 의미가 절반 사라진다.
            //    ⚠ 그래서 위의 «못 찾은 줄 정리» 에도 안 걸린다: findByIds 가 대기 상품도 돌려준다.
            boolean available = product.status() == ProductStatus.SELLING
                    && !product.deleted()
                    && variant.stock() >= qty;
            String thumb = product.images().isEmpty() ? null : product.images().get(0).thumbUrl();
            // 단일 옵션 상품은 옵션명을 감춘다("기본" 노이즈 방지). 옵션이 2개 이상일 때만 보여준다.
            String optionName = product.variants().size() > 1 ? variant.name() : null;
            // ⚠ regularPrice 는 **세일 전 판매가**다(G-9) — 주문이 이걸 스냅샷한다.
            //    listPrice(정가)와 다른 값이라 한 칸에 섞지 않는다.
            lines.add(new CartItemResponse(product.id(), variantId, product.name(), optionName,
                    variant.price(), variant.regularPrice(), product.listPrice(), product.status(),
                    qty, lineTotal, available, thumb));
            totalQuantity += qty;
            totalPrice += lineTotal;
        }
        long shippingFee = shippingPolicy.feeFor(totalPrice);
        return new CartResponse(lines, totalQuantity, totalPrice,
                shippingFee, totalPrice + shippingFee, shippingPolicy.amountUntilFree(totalPrice));
    }
}
