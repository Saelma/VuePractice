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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 생성·읽음·설정 변경 (2026-07-24). 알림을 "만드는" 유일한 입구다 —
 * stub 이던 이벤트 핸들러(주문·재고)가 이걸 호출해 실제 알림함에 쌓고 SSE 로 민다.
 */
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

    /** 알림 타입 켜기/끄기 — 없으면 만들고 있으면 바꾼다(upsert). */
    @Transactional
    public void changeSetting(UUID memberId, NotificationType type, boolean enabled) {
        prefRepository.findByMemberIdAndType(memberId, type)
                .ifPresentOrElse(
                        pref -> pref.change(enabled),
                        () -> prefRepository.save(NotificationPref.of(memberId, type, enabled)));
    }
}
