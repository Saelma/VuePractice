package com.glassvue.domain.notification;

import com.glassvue.domain.inquiry.event.InquiryAnsweredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 문의 이벤트 리스너(어댑터). {@link StockEventListener}·{@link OrderEventListener} 와 같은 규약 —
 * <b>수신·위임만, 로직 없음</b>.
 *
 * <ul>
 *   <li>{@code AFTER_COMMIT}: 답변 저장이 <b>커밋된 뒤에만</b> 알린다. 롤백되면 답변은 없던 일이
 *       되는데 알림만 남으면, 눌러 들어간 사용자가 <b>답변 없는 문의</b>를 본다.</li>
 *   <li>{@code @Async}: 알림 처리를 관리자의 답변 요청 스레드에서 분리(이벤트 풀 event-*).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class InquiryEventListener {

    private final InquiryNotificationHandler inquiryNotificationHandler;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInquiryAnswered(InquiryAnsweredEvent event) {
        inquiryNotificationHandler.handle(event);
    }
}
