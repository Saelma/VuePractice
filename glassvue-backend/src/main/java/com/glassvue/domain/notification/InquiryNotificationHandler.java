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
        String link = linkFor(event);

        notificationService.create(event.authorId(), NotificationType.INQUIRY,
                "문의 답변", message, link);
        log.info("[문의답변] inquiry={} author={} link={} 알림 생성",
                event.inquiryId(), event.authorId(), link);
    }

    /**
     * 🔴 <b>답변이 어디에 있는지가 문의마다 다르다</b> (2026-08-07, G-3 2단계).
     *
     * <p>원래는 갈래가 없었다 — 문의는 <b>자기 URL 이 없어서</b> 상품 페이지의 문의 섹션 앵커로만
     * 보냈다(B-15). 그 판단은 그때 옳았다. <b>모든 문의에 상품이 있었기 때문이다.</b>
     *
     * <p>2단계가 그 전제를 깼다. 일반 고객센터 문의는 {@code productId} 가 null 이라 옛 코드 그대로면
     * 링크가 <b>{@code /products/null#inquiries}</b> 가 된다. ⚠ 그리고 이건 <b>서버 로그에 아무것도
     * 안 남는다</b> — 잘못된 건 코드 경로가 아니라 <b>문자열</b>이라 예외도 에러도 없다. 답변은 멀쩡히
     * 달리고 알림도 도착하는데 <b>누르면 깨진 페이지로 간다.</b>
     *
     * <p>⚠ <b>{@code type} 이 아니라 {@code productId} 로 가른다.</b> 둘은 짝이라 결과가 같지만
     * ({@code ck_inquiry_product_pair}), 여기서 필요한 것은 «무슨 유형인가» 가 아니라 <b>«상품 URL 을
     * 만들 수 있는가»</b> 다. productId 가 없으면 만들 재료가 없다 — 그 사실을 그대로 묻는다.
     */
    private String linkFor(InquiryAnsweredEvent event) {
        if (event.productId() == null) {
            // 일반 문의의 주소는 「내 문의」다 — 목록 안에서 그 줄로 스크롤한다(G-3 3단계).
            return "/support#inquiry-" + event.inquiryId();
        }
        return "/products/" + event.productId() + "#inquiries";
    }
}
