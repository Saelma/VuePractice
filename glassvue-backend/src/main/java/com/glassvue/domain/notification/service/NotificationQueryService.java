package com.glassvue.domain.notification.service;

import com.glassvue.domain.notification.dto.NotificationResponse;
import com.glassvue.domain.notification.dto.NotificationSettingResponse;
import com.glassvue.domain.notification.entity.NotificationPref;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.repository.NotificationPrefRepository;
import com.glassvue.domain.notification.repository.NotificationRepository;
import com.glassvue.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;
    private final NotificationPrefRepository prefRepository;

    public PageResponse<NotificationResponse> myNotifications(UUID memberId, Pageable pageable) {
        return PageResponse.from(
                notificationRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable)
                        .map(NotificationResponse::from));
    }

    public long unreadCount(UUID memberId) {
        return notificationRepository.countByMemberIdAndReadFalse(memberId);
    }

    /**
     * 내 알림 설정 — <b>모든 타입</b>을 내려준다. 끈 것만 DB 에 있으므로, 행이 없는 타입은 켜짐(기본 on)으로 채운다
     * (화면이 전 타입 토글을 그려야 하니 서버가 기본값까지 완성해 준다).
     */
    public List<NotificationSettingResponse> settings(UUID memberId) {
        Map<NotificationType, Boolean> saved = prefRepository.findByMemberId(memberId).stream()
                .collect(Collectors.toMap(NotificationPref::getType, NotificationPref::isEnabled));
        return List.of(NotificationType.values()).stream()
                .map(type -> new NotificationSettingResponse(
                        type, type.label(), saved.getOrDefault(type, true)))
                .toList();
    }
}
