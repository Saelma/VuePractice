package com.glassvue.domain.notification;

import static org.mockito.Mockito.verify;

import com.glassvue.domain.inquiry.event.InquiryAnsweredEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 3층 컨벤션 고정 — 리스너(어댑터)는 로직 없이 Handler 에 위임만 한다({@link StockEventListenerTest} 와 같은 규약). */
@ExtendWith(MockitoExtension.class)
class InquiryEventListenerTest {

    @Mock InquiryNotificationHandler inquiryNotificationHandler;
    @InjectMocks InquiryEventListener listener;

    @Test
    @DisplayName("리스너는 로직 없이 Handler에 위임만 한다")
    void delegatesToHandler() {
        InquiryAnsweredEvent event = new InquiryAnsweredEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "배송 언제 오나요");
        listener.onInquiryAnswered(event);
        verify(inquiryNotificationHandler).handle(event);
    }
}
