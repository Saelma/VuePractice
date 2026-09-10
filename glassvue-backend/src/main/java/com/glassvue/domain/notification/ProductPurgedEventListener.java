package com.glassvue.domain.notification;

import com.glassvue.domain.catalog.event.ProductPurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 상품 영구 삭제 리스너(어댑터). {@code StockEventListener} 와 같은 규약 — 수신·위임만, 로직 없음.
 *
 * <p>{@code AFTER_COMMIT}: 상품 삭제가 <b>커밋된 뒤에만</b> 치운다.
 * 🔴 롤백되면 상품이 살아 있으므로 알림도 살아 있어야 한다 — 순서를 뒤집으면
 * <b>안 지워진 상품의 알림이 사라진다.</b>
 */
@Component
@RequiredArgsConstructor
public class ProductPurgedEventListener {

    private final ProductPurgedNotificationHandler handler;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductPurged(ProductPurgedEvent event) {
        handler.handle(event);
    }
}
