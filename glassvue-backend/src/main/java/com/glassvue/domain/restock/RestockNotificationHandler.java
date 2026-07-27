package com.glassvue.domain.restock;

import com.glassvue.domain.catalog.event.StockReplenishedEvent;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.service.NotificationCommandService;
import com.glassvue.domain.restock.repository.RestockSubscriptionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재입고 알림 처리 — 이벤트에 반응하는 "진짜 주체". 상품이 다시 들어오면 그 상품 신청자 전원에게
 * 인앱 알림을 만들고, 그 상품 구독을 통째로 비운다(재입고는 일회성이라 한 번 보내면 신청은 소진된다).
 *
 * <p>알림 생성은 notification 도메인의 공개 입구({@link NotificationCommandService#create})로만 한다
 * (그 안에서 타입별 opt-out 도 존중된다 — "재입고 알림"을 끈 회원에겐 안 만든다). 구독 저장소는
 * restock 자신의 것이라 직접 읽고 지운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestockNotificationHandler {

    private final RestockSubscriptionRepository subscriptionRepository;
    private final NotificationCommandService notificationService;

    @Transactional
    public void handle(StockReplenishedEvent event) {
        List<UUID> memberIds = subscriptionRepository.findMemberIdsByProductId(event.productId());
        if (memberIds.isEmpty()) {
            return; // 신청자 없음 — 조용히 끝
        }
        String title = "재입고 알림";
        String message = event.productName() + " 상품이 재입고되었습니다.";
        String link = "/products/" + event.productId();

        for (UUID memberId : memberIds) {
            notificationService.create(memberId, NotificationType.RESTOCK, title, message, link);
        }
        subscriptionRepository.deleteByProductId(event.productId());
        log.info("[재입고] product={} 신청자 {}명에게 알림", event.productId(), memberIds.size());
    }
}
