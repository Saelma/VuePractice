package com.glassvue.domain.notification.service;

import com.glassvue.domain.notification.dto.NotificationResponse;
import com.glassvue.domain.notification.entity.Notification;
import com.glassvue.domain.notification.entity.NotificationPref;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.repository.NotificationPrefRepository;
import com.glassvue.domain.notification.repository.NotificationRepository;
import com.glassvue.domain.notification.sse.NotificationStream;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 생성·읽음·설정 변경 (2026-07-24). 알림을 "만드는" 유일한 입구다 —
 * stub 이던 이벤트 핸들러(주문·재고)가 이걸 호출해 실제 알림함에 쌓고 SSE 로 민다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final NotificationPrefRepository prefRepository;
    private final NotificationStream stream;

    /**
     * 알림 한 건 생성 + 실시간 푸시. 설정에서 그 타입을 껐으면 <b>만들지 않는다</b>(opt-out).
     *
     * <p>저장 뒤 SSE 로 미는데, 페이로드가 알림 전체라 화면은 재조회 없이 목록·토스트에 바로 넣는다.
     * 푸시는 best-effort — 실패해도(끊긴 연결 등) 알림은 DB 에 남아 재조회 때 보인다.
     */
    @Transactional
    public void create(UUID memberId, NotificationType type, String title, String message, String link) {
        boolean enabled = prefRepository.findByMemberIdAndType(memberId, type)
                .map(NotificationPref::isEnabled)
                .orElse(true); // 행이 없으면 켜짐(기본 on)
        if (!enabled) {
            return;
        }
        Notification saved = notificationRepository.save(Notification.of(memberId, type, title, message, link));
        stream.push(memberId, NotificationResponse.from(saved));
    }

    /** 읽음 처리 — 본인 알림만. 멱등(이미 읽었어도 정상). */
    @Transactional
    public void markRead(UUID id, UUID memberId) {
        Notification notification = notificationRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead();
    }

    /** 모두 읽음. 안읽은 것만 벌크 UPDATE. */
    @Transactional
    public void markAllRead(UUID memberId) {
        notificationRepository.markAllRead(memberId);
    }

    /**
     * 알림 타입 켜기/끄기 — 없으면 만들고 있으면 바꾼다(upsert).
     *
     * <p><b>UPDATE 먼저, 없으면 INSERT.</b> find→insert 로 하면 같은 (member,type)의 빠른 연속 토글이
     * 둘 다 "없음"으로 읽고 각자 INSERT 해 유니크 제약(ORA-00001)에 걸린다(2026-07-24 실측).
     * 재토글은 순수 UPDATE 라 그 경합이 사라진다. 화면도 요청 중 토글을 잠가 최초 동시삽입까지 막는다.
     */
    @Transactional
    public void changeSetting(UUID memberId, NotificationType type, boolean enabled) {
        int updated = prefRepository.updateEnabled(memberId, type, enabled);
        if (updated == 0) {
            prefRepository.save(NotificationPref.of(memberId, type, enabled));
        }
    }

    /**
     * 회원 삭제 정리(F-1) — 알림과 <b>알림 설정</b>을 함께 지운다.
     *
     * <p>⚠ 설정({@code notification_pref})은 백로그의 F-1 목록에 없었다 — 회원별 행인데 빠져 있었다.
     * "회원 id 를 들고 있는 엔티티"를 코드에서 전수로 뽑아야 보이는 자리다(2026-07-30).
     */
    @Transactional
    public void deleteAllForMember(UUID memberId) {
        long notifications = notificationRepository.deleteByMemberId(memberId);
        long prefs = prefRepository.deleteByMemberId(memberId);
        log.info("Notifications deleted for member {}: notifications={} prefs={}", memberId, notifications, prefs);
    }
}
