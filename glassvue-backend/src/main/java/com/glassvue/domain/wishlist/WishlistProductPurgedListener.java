package com.glassvue.domain.wishlist;

import com.glassvue.domain.catalog.event.ProductPurgedEvent;
import com.glassvue.domain.wishlist.service.command.WishlistCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 상품이 영구 삭제되면 그 상품을 찜한 줄을 치운다 (2026-09-11, BACKLOG O-7) — 어댑터, 위임만 한다.
 *
 * <p>⚠ <b>같은 트랜잭션</b>({@code @EventListener})이다 — 탈퇴 정리({@link WishlistMemberWithdrawnListener})와 같은 모양.
 * 정리가 실패하면 영구 삭제도 롤백돼 다음 회차에 다시 시도한다 — <b>고아가 남지 않는다.</b>
 * (알림 정리가 커밋 뒤 비동기인 것은 알림 도메인의 관례다.)
 */
@Component
@RequiredArgsConstructor
public class WishlistProductPurgedListener {

    private final WishlistCommandService wishlistCommandService;

    @EventListener
    public void on(ProductPurgedEvent event) {
        wishlistCommandService.deleteAllForProduct(event.productId());
    }
}
