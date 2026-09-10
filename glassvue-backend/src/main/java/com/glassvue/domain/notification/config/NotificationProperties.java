package com.glassvue.domain.notification.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * notification.* 설정 (2026-09-10, BACKLOG F-2).
 *
 * <p>🔴 <b>보관 기간이 «읽음» 과 «안 읽음» 으로 갈린다.</b> F-2 의 최소안은 *"N일 지난 «읽은» 알림
 * 삭제"* 였는데, 2026-09-10 실측이 그것만으로는 부족하다고 말했다 —
 * <b>안 읽은 알림이 94건(45%)</b> 이고, 가장 많이 쌓인 관리자 계정은 <b>33건이 전부 미읽음</b>이다.
 * ⚠ 읽은 것만 지우면 <b>가장 많이 쌓인 축이 통째로 남는다</b> — 안 읽는 사람의 알림이 무한정 는다.
 *
 * @param readRetentionDays   <b>읽은</b> 알림을 며칠 두나. 이미 봤고 내용의 원본(주문·상품)은
 *                            그대로 있으므로 짧아도 잃는 것이 없다.
 * @param unreadRetentionDays <b>안 읽은</b> 알림을 며칠 두나. 🔴 <b>훨씬 길게 잡는다</b> —
 *                            안 본 것을 지우는 것은 «못 보게 만드는» 일이라 더 보수적이어야 한다.
 *                            ⚠ 그래도 지우는 이유: 알림의 값은 «지금 가서 보라» 인데
 *                            반년 전 «발송했어요» 는 그 값이 이미 없다(M-4 에서 쓴 판단과 같다).
 * @param cleanupEnabled      배치 on/off. ⚠ <b>끄면 보관이 무한이 된다</b>(아무것도 안 지워진다) —
 *                            «안 지워지는 것» 이 «잘못 지워지는 것» 보다 나으므로 그쪽이 안전한 기본값이다.
 *                            {@code catalog.purge-enabled}·{@code image.cleanup-enabled} 와 같은 자리.
 */
@Validated
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        /** 🔴 <b>0 이면 읽는 즉시 사라진다</b> — 알림함이 «읽으면 없어지는 곳» 이 된다. */
        @Positive int readRetentionDays,
        /** 🔴 <b>0 이면 안 읽은 것도 즉시 사라진다</b> — 알림 기능이 통째로 무의미해진다. */
        @Positive int unreadRetentionDays,
        boolean cleanupEnabled) {

    /**
     * 🔴 <b>안 읽은 것을 읽은 것보다 짧게 둘 수 없다.</b>
     *
     * <p>이 레코드의 설명이 *«안 본 것을 지우는 것은 «못 보게 만드는» 일이라 더 보수적이어야 한다»*
     * 라고 적어 뒀다. ⚠ <b>그 판단을 주석에만 두면 설정 한 줄로 뒤집힌다</b> —
     * 값이 뒤집히면 «읽었다고 표시한 것이 안 읽은 것보다 오래 남는» 이상한 상태가 되고,
     * 그건 <b>기동할 때 알아야</b> 할 어긋남이지 나중에 알림이 사라진 뒤 알 일이 아니다.
     */
    public NotificationProperties {
        if (readRetentionDays > 0 && unreadRetentionDays > 0 && unreadRetentionDays < readRetentionDays) {
            throw new IllegalArgumentException(
                    "notification.unread-retention-days(%d)는 read-retention-days(%d)보다 짧을 수 없다 — 안 본 것을 더 짧게 두면 «못 보게 만드는» 쪽이 된다"
                            .formatted(unreadRetentionDays, readRetentionDays));
        }
    }
}
