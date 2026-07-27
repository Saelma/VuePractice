package com.glassvue.domain.restock;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.glassvue.domain.catalog.event.StockReplenishedEvent;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.service.NotificationCommandService;
import com.glassvue.domain.restock.repository.RestockSubscriptionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestockNotificationHandlerTest {

    @Mock RestockSubscriptionRepository subscriptionRepository;
    @Mock NotificationCommandService notificationService;
    @InjectMocks RestockNotificationHandler handler;

    @Test
    @DisplayName("재입고 시 신청자 전원에게 RESTOCK 알림을 만들고, 그 상품 구독을 비운다(일회성)")
    void notifiesEachSubscriberAndClears() {
        UUID productId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(subscriptionRepository.findMemberIdsByProductId(productId)).thenReturn(List.of(a, b));

        handler.handle(new StockReplenishedEvent(productId, "무선키보드"));

        verify(notificationService).create(ArgumentMatchers.eq(a), ArgumentMatchers.eq(NotificationType.RESTOCK),
                ArgumentMatchers.eq("재입고 알림"), ArgumentMatchers.contains("재입고"),
                ArgumentMatchers.eq("/products/" + productId));
        verify(notificationService).create(ArgumentMatchers.eq(b), ArgumentMatchers.eq(NotificationType.RESTOCK),
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
        verify(subscriptionRepository).deleteByProductId(productId); // 재알림 방지
    }

    @Test
    @DisplayName("신청자가 없으면 알림도 삭제도 없다 — 조용히 끝난다")
    void noSubscribersNoop() {
        UUID productId = UUID.randomUUID();
        when(subscriptionRepository.findMemberIdsByProductId(productId)).thenReturn(List.of());

        handler.handle(new StockReplenishedEvent(productId, "무선키보드"));

        verifyNoInteractions(notificationService);
        verify(subscriptionRepository, never()).deleteByProductId(ArgumentMatchers.any());
    }
}
