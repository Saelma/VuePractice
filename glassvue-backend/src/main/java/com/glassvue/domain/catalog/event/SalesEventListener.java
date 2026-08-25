package com.glassvue.domain.catalog.event;

import com.glassvue.domain.catalog.service.command.SalesSyncHandler;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderItemCancelledEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import com.glassvue.domain.order.event.OrderReturnedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 판매량 집계 이벤트 리스너(어댑터). ReviewEventListener·OrderEventListener와 같은 규약 — 수신·위임만.
 *
 * <p>구독자가 catalog에 있는 이유: 갱신 대상이 product의 비정규화 컬럼({@code sold_count})이라
 * <b>catalog 소유 데이터</b>다. order는 이 리스너의 존재를 모르고, catalog는 order 서비스를 호출하지 않는다
 * (별점을 review 이벤트로 받는 것과 같은 방식 — 순환 없음).
 *
 * <p>주문(placed) → 증가, 취소·반품(cancelled·returned) → 감소. 배송완료(delivered)는 이미 주문 시점에
 * 센 것이라 건드리지 않는다.
 *
 * <p>AFTER_COMMIT — 주문이 롤백되면 판매량도 바뀌면 안 된다. @Async — checkout 응답이 이 갱신을 기다리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SalesEventListener {

    private final SalesSyncHandler salesSyncHandler;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        salesSyncHandler.increase(event.lines());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        salesSyncHandler.decrease(event.lines());
    }

    /**
     * 부분 취소 (2026-08-25, G-10). 🔴 <b>전체 취소와 «둘 다» 있어야 한다</b> — 부분 취소는
     * {@code OrderCancelledEvent} 를 전량이 빠질 때만 내므로, 이 구독이 없으면 부분 취소한 만큼
     * 판매량이 안 줄어든다. 반품 쪽은 {@code OrderReturnedEvent} 하나가 부분·전체를 다 덮는다
     * (승인이 한 경로뿐이라 갈릴 자리가 없다).
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderItemCancelled(OrderItemCancelledEvent event) {
        salesSyncHandler.decrease(event.lines());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderReturned(OrderReturnedEvent event) {
        salesSyncHandler.decrease(event.lines());
    }
}
