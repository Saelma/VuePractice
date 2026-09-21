package com.glassvue.domain.notification;

import com.glassvue.domain.member.service.MemberService;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.service.NotificationCommandService;
import com.glassvue.domain.order.event.OrderReturnRequestedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 반품 요청 알림 처리 — 이벤트에 반응하는 "진짜 주체" (2026-08-12, 08-11 이월).
 *
 * <p><b>왜 {@link OrderNotificationHandler} 에 넣지 않았나</b>: 그쪽은 <b>구매자에게</b> 알리는
 * 자리다(클래스 주석이 그렇게 못 박고 있다). 반품 «요청» 은 <b>관리자에게</b> 가므로
 * {@link StockAlertHandler} 와 같은 자리다 — 대상이 다르면 핸들러가 다르다.
 *
 * <p>대상은 관리자 회원 전원({@link MemberService#adminIds()})이다. member 공개 API 로만 접근한다
 * (도메인 경계). ⚠ {@code adminIds()} 는 2026-08-11 에 <b>SUPER_ADMIN 을 포함하도록</b> 고쳤다 —
 * 그전이었다면 최고 관리자만 이 알림을 못 받는 상태가 됐다.
 *
 * <p>🔴 <b>알림을 요청자에게는 보내지 않는다.</b> 반품을 요청한 것은 <b>자기가 방금 한 일</b>이라
 * 알려 줄 것이 없다 — 요청 결과(승인·거절)는 그때 {@code OrderNotificationHandler} 가 알린다.
 * ⚠ 관리자가 자기 주문을 반품 요청하면 «자기 알림» 을 받는데, 그건 <b>관리자로서 받는 것</b>이라
 * 맞다(대상 선정 기준이 «관리자인가» 하나뿐이라 갈릴 자리가 없다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnRequestAlertHandler {

    private final MemberService memberService;
    private final NotificationCommandService notificationService;

    public void handle(OrderReturnRequestedEvent event) {
        // 🔴 «무엇을 몇 개» 를 말한다 (2026-09-21, BACKLOG §I-11). 전엔 주문번호와 사유만 있어서
        //    관리자가 **열어 보기 전에는 규모를 몰랐다** — 부분 반품(G-10) 뒤로는 그게 정보다.
        //    ⚠ 요약도 사유와 **같은 규칙**으로 갈라 준다: 비어 있으면 그 자리를 아예 안 만든다
        //       («… 반품을 요청했습니다» 가 « 반품을» 로 시작해 빈칸이 보이면 값이 지워진 것처럼 읽힌다).
        String what = (event.itemsSummary() == null || event.itemsSummary().isBlank())
                ? "반품을"
                : event.itemsSummary() + " 반품을";
        String head = event.buyerNickname() + "님이 " + what + " 요청했습니다.";
        // 사유는 선택값이다. 없을 때 "사유: " 로 끝나면 값이 지워진 것처럼 읽힌다.
        String message = (event.reason() == null || event.reason().isBlank())
                ? head + " (사유 미입력)"
                : head + " 사유: " + event.reason();
        String link = "/orders/" + event.orderId();

        // ⚠ 링크는 주문 상세다 — 승인·거절을 하는 자리가 거기다(관리자는 남의 주문도 열 수 있다,
        //    OrderService.get 의 isAdmin 갈래). 2026-08-11 §10 의 «안내는 가리키는 곳이 있어야
        //    안내다» 를 지키려고 확인하고 골랐다.
        for (UUID adminId : memberService.adminIds()) {
            notificationService.create(adminId, NotificationType.RETURN_REQUEST,
                    "반품 요청 (" + event.orderNo() + ")", message, link);
        }
        log.info("[알림] 반품 요청 — order={} orderNo={} member={}",
                event.orderId(), event.orderNo(), event.memberId());
    }
}
