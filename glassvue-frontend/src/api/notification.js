import { apiGet, apiPost, apiPut } from './client';

/**
 * 인앱 알림 (2026-07-24). 실시간 스트림(SSE)은 stores/notifications.js 가 fetch 로 직접 연다
 * (EventSource 는 Authorization 헤더를 못 실어서 — 우리 JWT 방식과 안 맞는다). 여기는 REST 만.
 */

/**
 * 마케팅 발송 대상 수 (2026-08-03, B-21 후속) — **동의자 수**를 준다.
 *
 * ⚠ 실제 발송은 이보다 **적을 수 있다** — 알림 설정에서 마케팅을 끈 회원이 빠지기 때문이다.
 * 화면이 "N명에게 발송됩니다" 라고 단정하면 거짓말이 된다("최대 N명" 으로 적는다).
 */
export function fetchMarketingAudience() {
  return apiGet('/api/admin/notifications/marketing/audience');
}

/**
 * 마케팅 알림 발송. 응답: `{ agreed, sent, optedOut }`.
 *
 * ⚠ **대상은 서버가 정한다** — 여기서 지정할 방법이 없고 그게 의도다
 * (동의하지 않은 회원에게 보내는 구멍을 만들지 않는다).
 * ⚠ **되돌릴 수 없다** — 만들어진 알림은 회수할 수 없다.
 */
export function sendMarketing(payload) {
  return apiPost('/api/admin/notifications/marketing', payload);
}

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
