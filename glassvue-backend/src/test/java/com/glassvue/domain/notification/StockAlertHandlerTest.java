package com.glassvue.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.glassvue.domain.catalog.event.StockRunningLowEvent;
import com.glassvue.domain.member.service.MemberService;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.service.NotificationCommandService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 재고 부족 알림 핸들러 (H-6, 2026-07-31) — {@link OrderNotificationHandlerTest} 와 같은 사각에서 나왔다
 * (JaCoCo 실측 **4% · 분기 0%**).
 *
 * <p>이 핸들러가 다른 알림과 갈리는 지점 둘:
 * <ol>
 *   <li><b>대상이 한 사람이 아니라 관리자 전원이다.</b> 재고는 관리자 관심사라
 *       {@link MemberService#adminIds()} 로 받아 <b>사람 수만큼</b> 만든다 — 한 명에게만 가거나
 *       루프가 통째로 빠져도 "알림은 정상 생성"이라 아무도 모른다.</li>
 *   <li><b>품절과 재고 부족은 다른 문구다.</b> 재고가 0인데 "0개 남았습니다" 라고 보내면
 *       관리자가 아직 팔 수 있는 줄 안다. 분기 하나가 문구 두 개를 가른다.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class StockAlertHandlerTest {

    @Mock MemberService memberService;
    @Mock NotificationCommandService notificationService;
    @InjectMocks StockAlertHandler handler;

    private final UUID productId = UUID.randomUUID();
    private final UUID adminA = UUID.randomUUID();
    private final UUID adminB = UUID.randomUUID();

    private StockRunningLowEvent event(long remaining) {
        return new StockRunningLowEvent(productId, "몽쉘", "12개입", remaining, 5L);
    }

    @Test
    @DisplayName("재고 부족 → **관리자 전원**에게 각각, 문구에 상품·옵션·남은 수량")
    void notifiesEveryAdmin() {
        given(memberService.adminIds()).willReturn(List.of(adminA, adminB));

        handler.handle(event(3L));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(eq(adminA), eq(NotificationType.STOCK),
                eq("재고 부족 알림"), message.capture(), eq("/products/" + productId));
        verify(notificationService).create(eq(adminB), eq(NotificationType.STOCK),
                eq("재고 부족 알림"), any(), eq("/products/" + productId));

        // 재고는 옵션마다라 어느 옵션인지가 없으면 관리자가 무엇을 채울지 모른다(C-8).
        assertThat(message.getValue()).contains("몽쉘", "12개입", "3개");
    }

    @Test
    @DisplayName("⚠ 재고 0 은 **품절**로 알린다 — \"0개 남았습니다\"면 아직 팔 수 있는 줄 안다")
    void soldOutUsesDifferentWording() {
        given(memberService.adminIds()).willReturn(List.of(adminA));

        handler.handle(event(0L));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(eq(adminA), eq(NotificationType.STOCK),
                eq("품절 알림"), message.capture(), eq("/products/" + productId));
        assertThat(message.getValue()).contains("품절").doesNotContain("남았습니다");
    }

    @Test
    @DisplayName("관리자가 없으면 알림도 없다 — 빈 목록에 대고 만들지 않는다")
    void noAdminsNoNotifications() {
        given(memberService.adminIds()).willReturn(List.of());

        handler.handle(event(1L));

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }
}
