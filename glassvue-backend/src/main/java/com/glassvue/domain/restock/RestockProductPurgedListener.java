package com.glassvue.domain.restock;

import com.glassvue.domain.catalog.event.ProductPurgedEvent;
import com.glassvue.domain.restock.service.command.RestockSubscriptionCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 상품이 영구 삭제되면 그 상품의 재입고 구독을 치운다 (2026-09-11, BACKLOG O-7) — 어댑터, 위임만 한다.
 *
 * <p>⚠ <b>같은 트랜잭션</b>이다 — 탈퇴 정리({@link RestockMemberWithdrawnListener})와 같은 모양.
 * 실패하면 영구 삭제도 롤백돼 다음 회차에 다시 시도한다.
 */
@Component
@RequiredArgsConstructor
public class RestockProductPurgedListener {

    private final RestockSubscriptionCommandService subscriptionCommandService;

    @EventListener
    public void on(ProductPurgedEvent event) {
        subscriptionCommandService.deleteAllForProduct(event.productId());
    }
}
