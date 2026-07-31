package com.glassvue.domain.notification;

import com.glassvue.domain.inquiry.event.InquiryAnsweredEvent;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.service.NotificationCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문의 답변 알림 처리 — 이벤트에 반응하는 <b>"진짜 주체"</b> (2026-07-31, B-15).
 *
 * <p>물어본 사람이 답을 못 보던 구조를 닫는다. 그전엔 알림 타입이 ORDER·STOCK·RESTOCK 셋뿐이었고
 * {@code InquiryCommandService.answer()} 는 이벤트를 발행하지 않아, <b>작성자가 문의 화면에 다시
 * 들어와 봐야</b> 답변을 알 수 있었다.
 *
 * <p>알림 생성은 {@link NotificationCommandService#create} 한 곳으로만 한다 — 그 안에서 타입별
 * opt-out 이 존중된다("문의 답변 알림"을 끈 회원에겐 만들어지지도 않는다). 재입고 핸들러와 같은 규칙.
 *
 * <p><b>스팸 걱정이 없는 이유</b>: 답변은 관리자가 손으로 다는 것이라 폭주하지 않는다. 게다가
 * 발행 자체가 <b>첫 답변에서만</b> 일어난다({@code InquiryAnsweredEvent} 주석) — 그래서 D 의
 * 「알림 실발송」이 걸어 둔 선행조건(*"임계치 재알림 스팸 해결"*)과는 무관한 자리다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InquiryNotificationHandler {

    private final NotificationCommandService notificationService;

    @Transactional
    public void handle(InquiryAnsweredEvent event) {
        String message = "문의하신 '" + event.inquiryTitle() + "' 에 답변이 등록되었습니다.";
        // 문의는 상품 상세 안에 붙어 있어 자기 URL 이 없다 → 상품 페이지의 문의 섹션 앵커로 보낸다.
        String link = "/products/" + event.productId() + "#inquiries";

        notificationService.create(event.authorId(), NotificationType.INQUIRY,
                "문의 답변", message, link);
        log.info("[문의답변] inquiry={} author={} 알림 생성", event.inquiryId(), event.authorId());
    }
}
