package com.glassvue.domain.notification;

import com.glassvue.domain.order.event.OrderReturnRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 이벤트 중 <b>관리자에게</b> 알리는 것들의 리스너(어댑터) — 2026-08-12.
 *
 * <p>🔴 <b>{@link OrderEventListener} 와 나눠 둔 것이 요점이다.</b> 그쪽 주석은
 * *"이 리스너의 메서드 목록이 곧 「고객에게 알리는 주문 사건」의 목록"* 이라고 적혀 있고,
 * <b>그 문장이 실제로 일을 했다</b> — 2026-08-11 에 반품 승인·거절이 빠진 것을 그 목록을 세어 잡았다.
 * 관리자 알림을 거기 섞으면 <b>그 목록이 두 가지를 뜻하게 되어 세는 값이 사라진다.</b>
 * ⚠ 그래서 여기는 <b>「관리자에게 알리는 주문 사건」의 목록</b>이다. 둘 다 «빠진 것 없나» 를
 * 셀 때 보는 자리이고, <b>어느 쪽에 넣을지는 「누가 받나」가 정한다.</b>
 *
 * <p>전송 관심사(트랜잭션·스레딩)는 {@link OrderEventListener} 와 같게 맞춘다:
 * {@code AFTER_COMMIT}(롤백엔 반응 안 함) + {@code @Async}(요청 스레드를 막지 않음).
 * 로직은 갖지 않고 Handler 에 위임한다.
 */
@Component
@RequiredArgsConstructor
public class AdminOrderEventListener {

    private final ReturnRequestAlertHandler returnRequestAlertHandler;

    /**
     * 반품 요청 (2026-08-12, 08-11 이월) — <b>관리자가 화면을 봐야만 알던 자리</b>였다.
     * 재고 부족은 알림이 가는데(`STOCK`) 반품 승인 대기는 안 갔다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderReturnRequested(OrderReturnRequestedEvent event) {
        returnRequestAlertHandler.handle(event);
    }
}
