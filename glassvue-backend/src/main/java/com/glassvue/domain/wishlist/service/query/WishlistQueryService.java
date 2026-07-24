package com.glassvue.domain.wishlist.service.query;

import com.glassvue.domain.catalog.dto.ProductResponse;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.wishlist.dto.WishlistItemResponse;
import com.glassvue.domain.wishlist.entity.Wishlist;
import com.glassvue.domain.wishlist.repository.WishlistRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜 목록 조회. 찜 행은 상품 id 만 갖고 있으므로 상품 정보는 catalog 의 <b>공개 서비스</b>에서 합성한다
 * (장바구니가 하는 것과 같은 방식 — catalog 엔티티·리포지토리를 직접 만지지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistQueryService {

    private final WishlistRepository wishlistRepository;
    private final ProductQueryService productQueryService;

    /** 내 찜 목록 — 최근에 찜한 것부터. */
    public List<WishlistItemResponse> myWishlist(UUID memberId) {
        List<Wishlist> rows = wishlistRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
        if (rows.isEmpty()) {
            return List.of();
        }

        // 상품을 **한 번에** 읽는다. 행마다 조회하면 찜 20개에 쿼리 20번(N+1)이 된다.
        List<UUID> productIds = rows.stream().map(Wishlist::getProductId).toList();
        Map<UUID, ProductResponse> products = productQueryService.findByIds(productIds).stream()
                .collect(Collectors.toMap(ProductResponse::id, Function.identity()));

        List<WishlistItemResponse> items = new ArrayList<>();
        for (Wishlist w : rows) {
            ProductResponse p = products.get(w.getProductId());
            if (p == null) {
                // 삭제된 상품. **여기서 지우지 않는다** — 조회(readOnly)가 데이터를 고치면
                // "목록만 봤는데 뭔가 사라지는" 예측 불가능한 동작이 된다.
                // 장바구니는 지우지만 그건 결제 직전이라 정합성이 더 중요한 자리다.
                // 찜은 그냥 빼고 보여준다(해제는 사용자가 하거나, 필요해지면 스위퍼를 둔다).
                continue;
            }
            items.add(new WishlistItemResponse(
                    p.id(), p.name(), p.price(), p.listPrice(), p.status(),
                    p.images().isEmpty() ? null : p.images().get(0).thumbUrl(),
                    p.averageRating(), p.reviewCount(),
                    !p.soldOut(),
                    w.getCreatedAt()));
        }
        return items;
    }

    /**
     * 내가 찜한 상품 id 목록 — 화면이 상품 목록·상세에서 하트를 채울지 판단하는 데 쓴다.
     *
     * <p><b>왜 별도 API 인가</b>: {@code ProductResponse} 에 {@code wishlisted} 필드를 넣는 게 편해 보이지만,
     * 그러려면 catalog 가 wishlist 를 알아야 해서 <b>도메인 순환</b>({@code wishlist → catalog} 가 이미 있다)이
     * 된다. 2026-07-20 에 상품 목록 별점을 이벤트+비정규화로 우회한 것과 <b>같은 문제</b>다.
     * 여기서는 비정규화할 값도 아니고(회원마다 다르다) 이벤트로 밀 것도 아니라,
     * <b>id 집합을 따로 주고 화면이 합치는</b> 게 가장 싸다.
     */
    public List<UUID> myProductIds(UUID memberId) {
        return wishlistRepository.findProductIdsByMemberId(memberId);
    }
}
