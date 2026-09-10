package com.glassvue.domain.notification;

import com.glassvue.domain.catalog.event.ProductPurgedEvent;
import com.glassvue.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품이 영구 삭제되면 <b>그 상품을 가리키던 알림을 치운다</b> — 이벤트에 반응하는 "진짜 주체"
 * (2026-09-10 · BACKLOG M-4).
 *
 * <p>🔴 <b>왜 지우나 — «누가 썼나» 로 갈린다.</b>
 * 리뷰·문의는 <b>사람이 쓴 것</b>이라 상품이 없어도 읽을 값이 있고, 화면이 상품 이름 없음을 이미 견딘다.
 * 알림은 <b>시스템이 보낸 것</b>이고 «가서 보라» 는 말뿐이라, 갈 곳이 없으면 <b>남은 것이 없다.</b>
 * ⚠ 회원 탈퇴에서 {@code deleteByMemberId} 로 알림을 지우는 것과 같은 판단이다(F-1).
 *
 * <p>⚠ <b>«링크만 죽이기» 를 안 골랐다.</b> 화면은 {@code if (n.link)} 가드가 있어 그것도 되지만
 * ({@code NotificationBell}·{@code NotificationToaster}), {@link com.glassvue.domain.notification.entity.Notification}
 * 이 {@code link} 를 {@code updatable = false} 로 <b>일부러 불변</b>으로 두었다 —
 * 「보낸 것은 안 바꾼다」는 규약을 한 칸 때문에 깨지 않는다.
 *
 * <p>⚠ <b>누르면 «깨진 페이지» 가 아니라 «막다른 길» 이었다</b>(2026-09-10 실측):
 * {@code GET /api/products/<없는 id>} 는 404 이고 화면은 그걸 잡아
 * *"상품을 찾을 수 없습니다"* 를 그린다. 🔴 <b>크래시가 아니라 갈 곳이 없는 줄이다</b> —
 * 그래서 급한 결함이 아니라 <b>치우는 일</b>로 다뤘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductPurgedNotificationHandler {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void handle(ProductPurgedEvent event) {
        int deleted = notificationRepository.deleteByProductLink(event.productId().toString());
        if (deleted > 0) {
            log.info("[알림] 영구 삭제된 상품을 가리키던 알림 {}건 정리 — productId={} name={}",
                    deleted, event.productId(), event.productName());
        }
    }
}
