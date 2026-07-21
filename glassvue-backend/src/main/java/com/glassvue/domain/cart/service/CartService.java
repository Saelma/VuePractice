package com.glassvue.domain.cart.service;

import com.glassvue.domain.cart.CartStore;
import com.glassvue.domain.cart.dto.CartItemAddRequest;
import com.glassvue.domain.cart.dto.CartItemResponse;
import com.glassvue.domain.cart.dto.CartResponse;
import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장바구니. 상태는 Redis(CartStore), 상품 정보는 catalog의 공개 서비스에서 합성한다.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartStore cartStore;
    private final ProductQueryService productQueryService;

    /** 상품 존재 확인 후 담기(수량 증가). */
    public void add(UUID memberId, CartItemAddRequest req) {
        productQueryService.get(req.productId()); // 없으면 404
        cartStore.add(memberId, req.productId(), req.quantity());
    }

    public void setQuantity(UUID memberId, UUID productId, long quantity) {
        productQueryService.get(productId);
        cartStore.set(memberId, productId, quantity);
    }

    public void remove(UUID memberId, UUID productId) {
        cartStore.remove(memberId, productId);
    }

    public void clear(UUID memberId) {
        cartStore.clear(memberId);
    }

    public CartResponse getCart(UUID memberId) {
        Map<UUID, Long> items = cartStore.items(memberId);
        if (items.isEmpty()) {
            return new CartResponse(List.of(), 0, 0);
        }

        Map<UUID, ProductResponse> products = productQueryService.findByIds(items.keySet()).stream()
                .collect(Collectors.toMap(ProductResponse::id, Function.identity()));

        List<CartItemResponse> lines = new ArrayList<>();
        long totalQuantity = 0;
        long totalPrice = 0;
        for (Map.Entry<UUID, Long> e : items.entrySet()) {
            ProductResponse p = products.get(e.getKey());
            if (p == null) {
                cartStore.remove(memberId, e.getKey()); // 삭제된 상품은 정리
                continue;
            }
            long qty = e.getValue();
            long lineTotal = p.price() * qty;
            boolean available = p.status() == ProductStatus.SELLING && p.stock() >= qty;
            String thumb = p.images().isEmpty() ? null : p.images().get(0).thumbUrl();
            lines.add(new CartItemResponse(p.id(), p.name(), p.price(), p.status(), qty, lineTotal, available, thumb));
            totalQuantity += qty;
            totalPrice += lineTotal;
        }
        return new CartResponse(lines, totalQuantity, totalPrice);
    }
}
