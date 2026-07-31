package com.glassvue.domain.notification;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.glassvue.domain.inquiry.event.InquiryAnsweredEvent;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.service.NotificationCommandService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 문의 답변 알림 핸들러 (B-15, 2026-07-31) — {@link RestockNotificationHandler} 와 같은 성격의 검증.
 *
 * <p>여기서 못박는 것은 <b>"누구에게"</b> 와 <b>"어디로 데려가나"</b> 다. 둘 다 틀려도 알림은
 * 정상적으로 생성되므로 컴파일·통합으로는 안 드러난다.
 */
@ExtendWith(MockitoExtension.class)
class InquiryNotificationHandlerTest {

    @Mock NotificationCommandService notificationService;
    @InjectMocks InquiryNotificationHandler handler;

    private final UUID inquiryId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();

    @Test
    @DisplayName("답변이 달리면 **작성자**에게 INQUIRY 알림 — 링크는 상품 문의 섹션 앵커")
    void createsNotificationForAuthor() {
        handler.handle(new InquiryAnsweredEvent(inquiryId, productId, authorId, "배송 언제 오나요"));

        verify(notificationService).create(
                eq(authorId),                       // 답변자(관리자)가 아니라 물어본 사람
                eq(NotificationType.INQUIRY),
                eq("문의 답변"),
                contains("배송 언제 오나요"),        // 어느 문의인지가 문구에 있어야 쓸모가 있다
                // 문의는 자기 URL 이 없다 — 상품 상세의 문의 섹션(id="inquiries")으로 보낸다.
                // 앵커가 빠지면 상품 맨 위로 떨어져 사용자가 문의를 다시 찾아 내려가야 한다.
                eq("/products/" + productId + "#inquiries"));
    }
}
