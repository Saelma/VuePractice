package com.glassvue.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.notification.config.NotificationProperties;
import com.glassvue.domain.notification.entity.Notification;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관 기간이 지난 알림을 지운다 (2026-09-10 · BACKLOG F-2).
 *
 * <p>🔴 <b>이 시험의 요점은 «안 읽은 알림도 지워지는가» 다.</b> F-2 의 최소안은
 * *"N일 지난 «읽은» 알림 삭제"* 였는데, 실측이 그것으로는 부족하다고 말했다 —
 * 가장 많이 쌓인 관리자 계정이 <b>33건 전부 미읽음</b>이라 읽은 것만 치우면 <b>하나도 안 줄어든다.</b>
 * ⚠ 그래서 «안 읽음도 (더 긴) 기간이 지나면 지워진다» 를 <b>대조군과 함께</b> 못박는다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@Transactional
class NotificationCleanupTest {

    @Autowired NotificationRepository notificationRepository;
    @Autowired EntityManager entityManager;
    @Autowired NotificationProperties properties;

    private final UUID member = UUID.randomUUID();

    /** 알림을 만들고 {@code created_at} 을 원하는 시각으로 박는다(정상 경로로는 «지금» 밖에 못 만든다). */
    private UUID aged(boolean read, Duration ago) {
        Notification n = notificationRepository.save(
                Notification.of(member, NotificationType.ORDER, "제목", "내용", "/orders/" + UUID.randomUUID()));
        if (read) {
            n.markRead();
        }
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE notification SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, Instant.now().minus(ago))
                .setParameter(2, uuidToBytes(n.getId()))
                .executeUpdate();
        entityManager.clear();
        return n.getId();
    }

    private boolean exists(UUID id) {
        return notificationRepository.findById(id).isPresent();
    }

    private int sweepRead(int days) {
        return notificationRepository.deleteOlderThan(true, Instant.now().minus(Duration.ofDays(days)));
    }

    private int sweepUnread(int days) {
        return notificationRepository.deleteOlderThan(false, Instant.now().minus(Duration.ofDays(days)));
    }

    @Test
    @DisplayName("읽은 알림은 보관 기간이 지나면 사라진다 — 기간 안의 것은 남는다(대조군)")
    void readOlderThanRetentionIsRemoved() {
        UUID old = aged(true, Duration.ofDays(40));
        UUID fresh = aged(true, Duration.ofDays(3));

        sweepRead(30);

        assertThat(exists(old)).as("40일 지난 읽은 알림은 지운다").isFalse();
        assertThat(exists(fresh)).as("3일 된 것은 남는다 — 대조군이 없으면 «전부 지웠다» 와 구별이 안 된다").isTrue();
    }

    @Test
    @DisplayName("🔴 안 읽은 알림도 (더 긴) 기간이 지나면 사라진다 — 최소안이었다면 남았을 자리")
    void unreadOlderThanRetentionIsAlsoRemoved() {
        UUID old = aged(false, Duration.ofDays(200));
        UUID fresh = aged(false, Duration.ofDays(100));

        sweepUnread(180);

        assertThat(exists(old))
                .as("🔴 안 읽었어도 200일이면 지운다 — 안 그러면 «안 읽는 사람» 의 알림이 무한정 는다")
                .isFalse();
        assertThat(exists(fresh)).as("100일은 아직 기간 안이다").isTrue();
    }

    @Test
    @DisplayName("🔴 두 기준은 서로 안 섞인다 — 읽은 것을 지우는 쓸기가 안 읽은 것을 안 건드린다")
    void readAndUnreadThresholdsDoNotBleed() {
        UUID unreadOld = aged(false, Duration.ofDays(40));   // 읽음 기준(30일)은 넘었지만 «안 읽음» 이다
        UUID readOld = aged(true, Duration.ofDays(40));

        sweepRead(30);

        assertThat(exists(readOld)).isFalse();
        assertThat(exists(unreadOld))
                .as("🔴 안 읽은 것은 읽음 기준으로 안 지운다 — 안 본 것을 30일에 지우면 «못 보게 만드는» 일이다")
                .isTrue();
    }

    /*
     * ⚠ **반환 건수는 단언하지 않는다.** 이 테스트는 운영과 같은 DB 에 붙고(`DB_HOST`),
     * 쓸기는 **테이블 전체**를 대상으로 한다 — 처음에 «내가 만든 1건» 을 기대했다가
     * **43** 이 나왔다(이미 쌓여 있던 읽은 알림들이다).
     * 🔴 롤백이라 피해는 없었지만, **공유 DB 에서 «몇 건 지웠나» 는 내 것만의 값이 아니다.**
     * → 단언은 전부 «내가 만든 행이 있나/없나» 로 한다. 그건 남의 행이 늘어도 안 흔들린다.
     */

    @Test
    @DisplayName("경계: 기준 «이전» 만 지운다 — 딱 그 시각은 안 지운다")
    void boundaryIsExclusive() {
        // 30일보다 «조금 덜» 지난 것 — 경계 바로 안쪽이다.
        UUID justInside = aged(true, Duration.ofDays(30).minusMinutes(5));
        UUID justOutside = aged(true, Duration.ofDays(30).plusMinutes(5));

        sweepRead(30);

        assertThat(exists(justInside)).as("경계 안쪽은 남는다").isTrue();
        assertThat(exists(justOutside)).as("경계 바깥은 지운다").isFalse();
    }

    /**
     * 🔴 <b>테스트에서는 배치가 꺼져 있어야 한다</b> — {@code build.gradle} 의
     * {@code systemProperty 'notification.cleanup-enabled', 'false'} 가 그 스위치다.
     *
     * <p>⚠ <b>왜 이것까지 시험하나</b>: 통합 테스트는 <b>운영과 같은 DB</b> 에 붙는다.
     * 스위치가 사라지면 전수 도중에 배치가 깨어나 <b>운영 알림이 조용히 지워진다</b> —
     * 그리고 **아무 테스트도 빨개지지 않는다**(지워진 것은 남의 행이라 단언에 안 걸린다).
     * 🔴 <b>«그 줄이 아직 있는가» 를 여기서 못박는다</b>(WA §3-6 — 스위치도 세어야 할 대상이다).
     *
     * <p>⚠ 2026-09-10 에는 지연(10분)이 전수(8분 25초)보다 길어 <b>우연히</b> 안 돌았다.
     * 우연은 설계가 아니다.
     */
    @Test
    @DisplayName("🔴 테스트에서는 알림 정리 배치가 꺼져 있다 — 켜지면 운영 데이터가 지워진다")
    void cleanupBatchIsDisabledUnderTest() {
        assertThat(properties.cleanupEnabled())
                .as("build.gradle 의 systemProperty 'notification.cleanup-enabled' 가 사라졌다")
                .isFalse();
    }

    private static byte[] uuidToBytes(UUID uuid) {
        return java.nio.ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
