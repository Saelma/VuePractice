package com.glassvue.domain.notification;

import com.glassvue.domain.catalog.event.StockRunningLowEvent;
import com.glassvue.domain.member.service.MemberService;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.service.NotificationCommandService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 재고 부족 알림 처리 — 이벤트에 반응하는 "진짜 주체". 전송수단(스프링 이벤트/RabbitMQ)과 무관한 순수 로직.
 *
 * <p>2026-07-24: 로그 stub 을 걷고 <b>관리자에게 인앱 알림</b>을 만든다. 재고는 관리자 관심사라
 * 관리자 회원 전원({@link MemberService#adminIds()})에게 보낸다. member 공개 API 로만 접근한다(도메인 경계).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockAlertHandler {

    private final MemberService memberService;
    private final NotificationCommandService notificationService;

    public void handle(StockRunningLowEvent event) {
        // 재고가 옵션(variant)마다라 어느 옵션인지 함께 담는다(2026-07-24, C-8).
        String label = event.productName() + " (" + event.variantName() + ")";
        boolean soldOut = event.remainingStock() == 0;
        String title = soldOut ? "품절 알림" : "재고 부족 알림";
        String message = soldOut
                ? label + " 옵션이 품절되었습니다."
                : label + " 재고가 " + event.remainingStock() + "개 남았습니다.";
        String link = "/products/" + event.productId();

        for (UUID adminId : memberService.adminIds()) {
            notificationService.create(adminId, NotificationType.STOCK, title, message, link);
        }
        log.warn("[재고] {} — {} product={} remaining={} threshold={}",
                title, label, event.productId(), event.remainingStock(), event.threshold());
    }
}
