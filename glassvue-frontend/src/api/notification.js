import { apiGet, apiPost, apiPut } from './client';

/**
 * 인앱 알림 (2026-07-24). 실시간 스트림(SSE)은 stores/notifications.js 가 fetch 로 직접 연다
 * (EventSource 는 Authorization 헤더를 못 실어서 — 우리 JWT 방식과 안 맞는다). 여기는 REST 만.
 */

// 내 알림 목록(페이징, 최신순). 반환: PageResponse<NotificationResponse>
export function fetchNotifications({ page = 0, size = 20 } = {}) {
  return apiGet('/api/notifications', { page, size });
}

// 안읽음 수(벨 뱃지). 반환: number
export function fetchUnreadCount() {
  return apiGet('/api/notifications/unread-count');
}

export function markNotificationRead(id) {
  return apiPost(`/api/notifications/${id}/read`);
}

export function markAllNotificationsRead() {
  return apiPost('/api/notifications/read-all');
}

// 알림 타입별 on/off. 반환: [{ type, label, enabled }]
export function fetchNotificationSettings() {
  return apiGet('/api/notifications/settings');
}

export function updateNotificationSetting(type, enabled) {
  return apiPut('/api/notifications/settings', { type, enabled });
}
