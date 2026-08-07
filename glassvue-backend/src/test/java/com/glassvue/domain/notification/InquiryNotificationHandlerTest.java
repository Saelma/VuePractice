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

    @Test
    @DisplayName("🔴 **일반 문의는 「내 문의」로 보낸다** — 상품이 없어 상품 URL 을 만들 수가 없다")
    void generalInquiry_linksToSupport() {
        // ⚠ 2026-08-07(G-3 2단계)에 열린 갈래다. 그전엔 모든 문의에 상품이 있어 갈래가 없었다.
        //   productId 가 null 인데 옛 코드 그대로면 링크가 "/products/null#inquiries" 가 된다 —
        //   그리고 그건 **서버 로그에 아무것도 안 남는다**(잘못된 건 코드 경로가 아니라 문자열이다).
        //   답변도 알림도 멀쩡한데 **누르면 깨진 페이지로 간다.**
        handler.handle(new InquiryAnsweredEvent(inquiryId, null, authorId, "환불 계좌를 바꾸고 싶어요"));

        verify(notificationService).create(
                eq(authorId),
                eq(NotificationType.INQUIRY),
                eq("문의 답변"),
                contains("환불 계좌를 바꾸고 싶어요"),
                eq("/support#inquiry-" + inquiryId));
    }

    @Test
    @DisplayName("⚠ 링크에 «null» 이라는 글자가 들어가지 않는다(문자열 조립이 조용히 틀리는 자리)")
    void generalInquiry_linkNeverContainsNull() {
        handler.handle(new InquiryAnsweredEvent(inquiryId, null, authorId, "제목"));

        // ⚠ 위 테스트는 «/support#inquiry-…» 라는 **정답**을 못 박고, 이건 «/products/null…» 이라는
        //   **오답의 모양**을 못 박는다. 링크 규칙이 나중에 또 바뀌어도 이 조건은 계속 참이어야 한다.
        verify(notificationService).create(
                eq(authorId), eq(NotificationType.INQUIRY), eq("문의 답변"), contains("제목"),
                org.mockito.ArgumentMatchers.argThat(link -> link != null && !link.contains("null")));
    }
}
