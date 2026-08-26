package com.glassvue.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.service.NotificationCommandService;
import com.glassvue.domain.order.event.OrderCancelledEvent;
import com.glassvue.domain.order.event.OrderItemCancelledEvent;
import com.glassvue.domain.order.event.OrderDeliveredEvent;
import com.glassvue.domain.order.event.OrderPlacedEvent;
import com.glassvue.domain.order.event.OrderReturnRejectedEvent;
import com.glassvue.domain.order.event.OrderReturnedEvent;
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
 * 주문 알림 핸들러 (H-6, 2026-07-31).
 *
 * <p><b>왜 이 파일이 이제야 생겼나</b>: H-5(JaCoCo)를 붙이고 나서야 이 클래스가 <b>4% · 분기 0%</b>,
 * 즉 <b>한 번도 실행된 적이 없다</b>는 게 드러났다. 같은 패키지의 {@link OrderEventListener} 는
 * 100% 였다 — <b>어댑터는 덮였고 진짜 주체는 비어 있었다.</b>
 *
 * <p>원인은 구조적이다. 핸들러는 {@code @Async} + {@code AFTER_COMMIT} 뒤에 있어서
 * {@code @Transactional} 통합 테스트에서는 <b>커밋이 없어 리스너가 뜨지 않는다.</b>
 * 통합을 아무리 늘려도 이 층은 안 돌아간다 → <b>핸들러는 직접 부른다</b>
 * ({@link InquiryNotificationHandlerTest}·{@code RestockNotificationHandlerTest} 와 같은 방식).
 *
 * <p>여기서 못박는 것은 <b>"누구에게 · 무엇을 · 어디로"</b> 다. 셋 다 틀려도 알림은 정상적으로
 * 생성되므로 컴파일·통합으로는 드러나지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class OrderNotificationHandlerTest {

    /** 알림 본문 맨 앞에 붙는 주문번호(2026-08-26) — 어느 주문인지 알림함에서 바로 읽히게. */
    private static final String ORDER_NO = "20260101-0001";

    @Mock NotificationCommandService notificationService;
    @InjectMocks OrderNotificationHandler handler;

    private final UUID orderId = UUID.randomUUID();
    private final UUID buyerId = UUID.randomUUID();

    /** 생성된 알림 한 건. 인자 다섯 개를 매번 캡처하는 잡음을 여기로 모은다. */
    private record Notified(UUID memberId, NotificationType type, String title, String message, String link) {}

    private Notified captured() {
        ArgumentCaptor<UUID> member = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<NotificationType> type = ArgumentCaptor.forClass(NotificationType.class);
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(
                member.capture(), type.capture(), title.capture(), message.capture(), link.capture());
        return new Notified(member.getValue(), type.getValue(),
                title.getValue(), message.getValue(), link.getValue());
    }

    // ── 주문 접수 ─────────────────────────────────────────────

    @Test
    @DisplayName("주문 접수 → **구매자**에게 ORDER 알림, 링크는 그 주문 상세")
    void placedNotifiesBuyer() {
        handler.handle(new OrderPlacedEvent(orderId, buyerId, ORDER_NO, 30_000L, 2, List.of()));

        Notified n = captured();
        assertThat(n.memberId()).isEqualTo(buyerId);   // 주문 id 가 아니다(둘 다 UUID 라 바꿔 써도 컴파일된다)
        assertThat(n.type()).isEqualTo(NotificationType.ORDER);
        assertThat(n.message()).contains("2건");        // 몇 건 샀는지가 없으면 알림이 무의미해진다
        assertThat(n.link()).isEqualTo("/orders/" + orderId);
    }

    // ── 배송완료 (⚠ 적립 문구 분기) ────────────────────────────

    @Test
    @DisplayName("배송완료 + 적립 → 적립액을 문구에 담는다")
    void deliveredWithPoint() {
        handler.handle(new OrderDeliveredEvent(orderId, buyerId, ORDER_NO, 50_000L, 500L));

        Notified n = captured();
        assertThat(n.memberId()).isEqualTo(buyerId);
        assertThat(n.message()).contains("500P 적립");
        assertThat(n.link()).isEqualTo("/orders/" + orderId);
    }

    @Test
    @DisplayName("⚠ 적립이 0이면 적립 문구를 **넣지 않는다** — \"0P 적립되었습니다\"는 안내가 아니라 혼란이다")
    void deliveredWithoutPoint() {
        handler.handle(new OrderDeliveredEvent(orderId, buyerId, ORDER_NO, 0L, 0L));

        Notified n = captured();
        assertThat(n.message()).isEqualTo(ORDER_NO + " · 배송이 완료되었어요.");
        assertThat(n.message()).doesNotContain("적립");
    }

    // ── 주문 취소 ─────────────────────────────────────────────

    @Test
    @DisplayName("주문 취소 → 구매자에게 취소 금액과 함께")
    void cancelledNotifiesBuyer() {
        // 🔴 **이 값은 «이번에 취소된 금액» 이지 주문 원금이 아니다** (2026-08-25, §I).
        //    실측(08-24 `20260824-5296`): 원본 35,000 중 25,000 이 이미 부분 취소로 빠진 뒤
        //    남은 **10,000** 을 취소하면서 알림이 «35,000원 취소» 라고 말했다.
        handler.handle(new OrderCancelledEvent(orderId, buyerId, ORDER_NO, 10_000L, 2, List.of()));

        Notified n = captured();
        assertThat(n.memberId()).isEqualTo(buyerId);
        assertThat(n.type()).isEqualTo(NotificationType.ORDER);
        assertThat(n.message()).contains("10000");     // 이번에 실제로 빠진 금액
        assertThat(n.message()).doesNotContain("35000");
        assertThat(n.link()).isEqualTo("/orders/" + orderId);
    }

    // ── 반품 (2026-08-11, 08-10 §16-4 4번 — 여기가 통째로 비어 있었다) ──────

    /**
     * ⚠ 배송완료의 적립 문구와 <b>같은 분기</b>다 — 값이 0이면 안 넣는다.
     * 「0원이 환불되었습니다」는 안내가 아니라 혼란이라는 판단을 반품 쪽에도 그대로 적용한다.
     */
    @Test
    @DisplayName("반품 승인 → 구매자에게 **환불 금액과 함께** (돈이 움직였는데 말이 없으면 안 된다)")
    void returnedNotifiesBuyer() {
        handler.handle(new OrderReturnedEvent(orderId, buyerId, ORDER_NO, 25_000L, List.of(), "ZZ상품 1개", true));

        Notified n = captured();
        assertThat(n.memberId()).isEqualTo(buyerId);
        assertThat(n.type()).isEqualTo(NotificationType.ORDER);
        assertThat(n.message()).contains("25000");
        assertThat(n.link()).isEqualTo("/orders/" + orderId);
    }

    /**
     * 🔴 <b>부분 반품인데 «반품이 완료되었어요» 라고 말하던 자리다</b> (2026-08-25, §8-6).
     *
     * <p>G-10 검증에서 <b>네 번</b> 거짓으로 나갔다 — 14개 중 3개만 반품했는데 «완료» 라고 알렸다.
     * ⚠ 흐름에 «부분» 을 넣고 <b>문구를 안 열었다</b>: WA §1-2-1 «남기는 쪽 ↔ 보는 쪽» 그대로다.
     * 🔴 그 짝이 «조용해서 오래 간다» 고 경고한 그대로 <b>돈은 정확했고 아무것도 안 터졌다.</b>
     */
    @Test
    @DisplayName("🔴 부분 반품이면 «일부» 라고 말하고 **무엇이 몇 개** 인지 밝힌다")
    void partialReturnSaysPartial() {
        handler.handle(new OrderReturnedEvent(orderId, buyerId, ORDER_NO, 12_858L, List.of(),
                "지바 1개, 반팔티 1개", false));

        Notified n = captured();
        assertThat(n.title()).isEqualTo("일부 반품이 완료되었어요");
        // 🔴 «반품이 완료되었어요» 로 시작하면 안 된다 — 고객이 주문 전체가 끝난 줄 읽는다.
        assertThat(n.message()).startsWith(ORDER_NO + " · 지바 1개, 반팔티 1개 반품이 완료되었어요.");
        assertThat(n.message()).contains("12858");
    }

    @Test
    @DisplayName("⚠ 대조군: 전량 반품이면 예전과 **같은 문구**다 (기존 주문의 알림은 안 바뀐다)")
    void fullReturnKeepsOldWording() {
        handler.handle(new OrderReturnedEvent(orderId, buyerId, ORDER_NO, 25_000L, List.of(), "ZZ상품 1개", true));

        Notified n = captured();
        assertThat(n.title()).isEqualTo("반품이 완료되었어요");
        assertThat(n.message()).startsWith(ORDER_NO + " · 반품이 완료되었어요.");
        // ⚠ 전량이면 «무엇이 몇 개» 를 안 붙인다 — 주문 전체라 굳이 열거할 이유가 없다.
        assertThat(n.message()).doesNotContain("ZZ상품");
    }

    // ── 🔴 부분 취소 — 여기가 통째로 비어 있었다 (2026-08-25, §I) ──────

    /**
     * 🔴 <b>부분 취소는 고객 알림이 아예 없었다</b> — 관리자가 대신 빼도 <b>아무 말이 없었다.</b>
     * 전체 취소는 알림이 가므로 <b>비대칭</b>이었고, 2026-08-11 이 반품에 대해 고친 것의 거울상이다.
     */
    @Test
    @DisplayName("🔴 부분 취소 → 무엇이 몇 개 빠졌고 얼마가 돌아오는지 알린다")
    void itemCancelledNotifiesBuyer() {
        handler.handle(new OrderItemCancelledEvent(orderId, buyerId, ORDER_NO, List.of(),
                "지바 1개", 8_000L, 2_000L, false));

        Notified n = captured();
        assertThat(n.memberId()).isEqualTo(buyerId);
        // 🔴 «주문이 취소되었어요» 가 아니다 — 1개만 빠졌는데 그러면 전체 취소로 읽힌다.
        assertThat(n.title()).isEqualTo("주문 일부가 취소되었어요");
        assertThat(n.message()).contains("지바 1개").contains("8000").contains("2000");
        assertThat(n.link()).isEqualTo("/orders/" + orderId);
    }

    /**
     * 🔴 <b>전량이 빠지면 여기서 말하지 않는다</b> — 뒤이어 {@code OrderCancelledEvent} 가 나가므로
     * «주문 일부가 취소되었어요» 와 «주문이 취소되었어요» 가 <b>연달아 두 번</b> 뜬다.
     * ⚠ 이벤트 자체는 계속 나간다(판매량 되돌림이 이 회차 몫을 여기에만 싣는다) —
     * <b>구독자마다 «무엇을 하느냐» 를 가르는</b> 자리다.
     */
    @Test
    @DisplayName("⚠ 마지막 품목을 뺀 부분 취소는 **알림을 안 낸다** (두 번 뜨지 않게)")
    void itemCancelledStaysQuietWhenOrderIsEmptied() {
        handler.handle(new OrderItemCancelledEvent(orderId, buyerId, ORDER_NO, List.of(),
                "지바 1개", 8_000L, 2_000L, true));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("⚠ 환불액이 0이면 금액 문구를 **넣지 않는다** (배송완료 적립과 같은 판단)")
    void returnedWithoutRefund() {
        handler.handle(new OrderReturnedEvent(orderId, buyerId, ORDER_NO, 0L, List.of(), "ZZ상품 1개", true));

        Notified n = captured();
        assertThat(n.message()).isEqualTo(ORDER_NO + " · 반품이 완료되었어요.");
        assertThat(n.message()).doesNotContain("환불");
    }

    /**
     * 🔴 셋 중 가장 급했던 자리 — 거절은 상태가 조용히 {@code DELIVERED} 로 돌아갈 뿐이라
     * 알림이 없으면 <b>요청해 놓고 영영 소식이 없다.</b>
     *
     * <p>🔴 <b>사유를 문구에 담는다</b>(2026-08-11 같은 날 고쳤다, V47). 처음엔
     * *"자세한 내용은 주문 상세에서 확인해 주세요"* 였는데 <b>그 상세에 아무것도 없었다</b> —
     * 알림이 <b>없는 곳을 가리켰다.</b> «없는 값을 지어내지 않는다» 를 지키느라 문장을 비웠지만,
     * 값이 없으면 문장을 비울 게 아니라 <b>값을 만들어야</b> 했다.
     */
    @Test
    @DisplayName("반품 거절 → 구매자에게 **사유와 함께** 알린다 (안내가 가리키는 곳에 내용이 있어야 한다)")
    void returnRejectedNotifiesBuyer() {
        handler.handle(new OrderReturnRejectedEvent(orderId, buyerId, ORDER_NO, "사용 흔적이 있습니다"));

        Notified n = captured();
        assertThat(n.memberId()).isEqualTo(buyerId);
        assertThat(n.type()).isEqualTo(NotificationType.ORDER);
        assertThat(n.title()).contains("거절");
        assertThat(n.message()).contains("사용 흔적이 있습니다");
        assertThat(n.link()).isEqualTo("/orders/" + orderId);
    }

    /**
     * ⚠ 사유는 필수라 운영에서 {@code null} 이 올 수 없지만, 옛 jar 가 만든 이벤트 등의 경계를 막는다 —
     * <b>«null» 이라는 글자가 고객 알림에 뜨는 것</b>이 최악이다.
     * ⚠ 이때도 «주문 상세에서 확인하라» 고 하지 <b>않는다</b> — 그 상세에도 사유가 없을 것이기 때문이다.
     * 같은 실수를 반복하지 않으려고 **고객센터**로 보낸다.
     */
    @Test
    @DisplayName("⚠ 거절 사유가 비면 「null」을 보여주지 않고 고객센터로 안내한다")
    void returnRejectedWithoutReason() {
        handler.handle(new OrderReturnRejectedEvent(orderId, buyerId, ORDER_NO, null));

        Notified n = captured();
        assertThat(n.message()).doesNotContain("null");
        assertThat(n.message()).contains("고객센터");
    }
    /**
     * 🔴 <b>여섯 개 알림이 «전부» 주문번호로 시작한다</b> (2026-08-26, 사용자 요청).
     *
     * <p>⚠ <b>이 테스트가 이 파일에서 유일하게 «전부» 를 세는 자리다.</b> 나머지 테스트는 알림을
     * <b>하나씩</b> 본다 — 그래서 새 알림이 생기거나 한 곳만 문구를 고치면 <b>그 하나만 낡는다.</b>
     * 🔴 이 저장소가 반복해서 데는 «짝의 비대칭» 이 정확히 그 모양이고(매출 식·상태 목록·부분 취소
     * 알림), 오늘만 §I-5·§I-6·§I-12 셋이 같은 이유로 나왔다.
     *
     * <p>⚠ <b>핸들러 메서드가 늘면 이 테스트는 «안 늘어난다»</b> — 컴파일도 안 깨진다.
     * 그래서 <b>여기에 «여섯» 이라는 개수를 적지 않고</b> 이벤트를 나열한다: 새 이벤트를 만든
     * 사람이 이 목록을 보고 한 줄 더하게 된다. (개수를 적으면 그 숫자가 낡는다 — WA §4-0.)
     */
    @Test
    @DisplayName("🔴 주문 알림은 **전부** 본문이 주문번호로 시작한다 — 알림함에서 어느 주문인지 알아야 한다")
    void everyOrderNotificationStartsWithOrderNo() {
        record Case(String what, Runnable fire) {}
        List<Case> cases = List.of(
                new Case("주문 접수", () ->
                        handler.handle(new OrderPlacedEvent(orderId, buyerId, ORDER_NO, 30_000L, 2, List.of()))),
                new Case("배송 완료", () ->
                        handler.handle(new OrderDeliveredEvent(orderId, buyerId, ORDER_NO, 50_000L, 500L))),
                new Case("주문 취소", () ->
                        handler.handle(new OrderCancelledEvent(orderId, buyerId, ORDER_NO, 10_000L, 2, List.of()))),
                new Case("부분 취소", () ->
                        handler.handle(new OrderItemCancelledEvent(orderId, buyerId, ORDER_NO, List.of(),
                                "지바 1개", 10_000L, 0L, false))),
                new Case("반품 승인", () ->
                        handler.handle(new OrderReturnedEvent(orderId, buyerId, ORDER_NO, 25_000L, List.of(),
                                "지바 1개", true))),
                new Case("반품 거절", () ->
                        handler.handle(new OrderReturnRejectedEvent(orderId, buyerId, ORDER_NO, "ZZ-사용 흔적"))));

        for (Case c : cases) {
            reset(notificationService);
            c.fire().run();
            assertThat(captured().message())
                    .as("«%s» 알림이 주문번호로 시작하지 않는다", c.what())
                    .startsWith(ORDER_NO + " · ");
        }
    }
}
