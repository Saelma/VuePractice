package com.glassvue.domain.notification;

import com.glassvue.domain.notification.config.NotificationProperties;
import com.glassvue.domain.notification.repository.NotificationRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관 기간이 지난 알림을 지운다 (2026-09-10, BACKLOG F-2).
 *
 * <p>{@code ProductPurgeScheduler}·{@code ImageCleanupScheduler} 와 <b>같은 모양</b>이다 —
 * 경과 시간 + {@code @Scheduled} + on/off 스위치 + 처리 건수 로그.
 *
 * <p>🔴 <b>왜 필요한가</b>: 알림은 이 시스템에서 <b>자동으로 생성되는 유일한 데이터</b>다.
 * 사용자가 안 지우면 반드시 늘고, 지우는 화면도 없다. 2026-09-10 실측 —
 * 한 관리자 계정에 <b>133건</b>(5주), 전체 <b>211건</b> 중 <b>안 읽음 94건(45%)</b>.
 *
 * <p>⚠ <b>«읽은 것만 지운다» 로는 부족하다</b>(F-2 최소안). 가장 많이 쌓인 관리자 계정이
 * <b>33건 전부 미읽음</b>이라, 읽은 것만 치우면 <b>그 계정은 하나도 안 줄어든다.</b>
 * 🔴 그래서 기준을 둘로 나눴다 — 읽은 것은 짧게, <b>안 읽은 것은 훨씬 길게.</b>
 *
 * <p>⚠ <b>지운 건수가 0이면 로그를 안 남긴다</b>(대부분의 실행이 0이다 — 상품·이미지 정리와 같은 판단).
 * 반대로 지웠으면 반드시 남긴다: 되돌릴 수 없는 일이라 «언제 몇 건이 사라졌나» 가 유일한 흔적이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationRepository notificationRepository;
    private final NotificationProperties properties;

    /**
     * 하루에 한 번. 기동 직후 바로 돌지 않게 지연을 둔다(검증 시 짧게 덮어쓸 수 있게 property 로).
     *
     * <p>⚠ 보관이 <b>일</b> 단위라 주기도 <b>일</b> 단위로 둔다 — 상품 purge 가 시간 단위인 것은
     * «목록에 안 보이는데 DB 에는 있는» 창을 줄이려는 것이고, 알림에는 그런 창이 없다.
     */
    @Scheduled(fixedDelayString = "${notification.cleanup-interval-ms:86400000}",
               initialDelayString = "${notification.cleanup-initial-delay-ms:600000}")
    @Transactional
    public void sweep() {
        if (!properties.cleanupEnabled()) {
            return;
        }
        Instant now = Instant.now();
        int read = notificationRepository.deleteOlderThan(
                true, now.minus(Duration.ofDays(properties.readRetentionDays())));
        int unread = notificationRepository.deleteOlderThan(
                false, now.minus(Duration.ofDays(properties.unreadRetentionDays())));

        if (read + unread == 0) {
            return;
        }
        log.info("[알림] 보관 기간 경과 {}건 삭제 — 읽음 {}건({}일 경과) · 안읽음 {}건({}일 경과)",
                read + unread, read, properties.readRetentionDays(),
                unread, properties.unreadRetentionDays());
    }
}
