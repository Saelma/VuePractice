import { apiGet } from './client';

/**
 * 적립금 · 회원 등급 (2026-07-24, 백로그 C-10).
 *
 * 적립·차감 API는 없다 — 적립은 배송완료가, 사용은 주문 생성이 한다.
 * 외부에서 잔액을 직접 바꿀 경로를 만들면 이력과 어긋날 여지가 생긴다.
 */

/** 내 적립금·등급. 다음 등급까지 남은 금액은 서버가 계산해 준다(화면이 임계값을 몰라도 된다). */
export function fetchPointAccount() {
  return apiGet('/api/points/me');
}

export function fetchPointHistory({ page = 0, size = 20 } = {}) {
  return apiGet('/api/points/me/history', { page, size });
}

// --- 관리자 조회 (B-11 회원 상세) — 특정 회원의 적립금·등급·이력. point 도메인이 소유해 admin 으로 노출. ---
export function fetchAdminMemberPointAccount(memberId) {
  return apiGet(`/api/admin/points/${memberId}/account`);
}
export function fetchAdminMemberPointHistory(memberId, { page = 0, size = 10 } = {}) {
  return apiGet(`/api/admin/points/${memberId}/history`, { page, size });
}

export const POINT_TYPE_LABEL = { EARN: '적립', USE: '사용', ADJUST: '조정', REFUND: '환불' };
export function pointTypeText(type) {
  return POINT_TYPE_LABEL[type] || type || '';
}

export const GRADE_LABEL = {
  BRONZE: '브론즈',
  SILVER: '실버',
  GOLD: '골드',
  VIP: 'VIP',
};

export function gradeText(grade) {
  return GRADE_LABEL[grade] || grade || '';
}

/**
 * 이 주문에 쓸 수 있는 적립금 상한.
 *
 * 서버와 **같은 규칙**이어야 한다 — 상품합계 − 쿠폰할인, 그리고 잔액. 둘 중 작은 값.
 * 배송비는 포함하지 않는다(적립금으로 운임을 내면 결제금액 계산이 이상해진다).
 * 화면이 먼저 막아야 서버에서 400을 받고 나서야 아는 일이 없다.
 */
export function maxUsablePoint(balance, itemsTotal, couponDiscount = 0) {
  const limit = Math.max(0, (itemsTotal || 0) - (couponDiscount || 0));
  return Math.max(0, Math.min(balance || 0, limit));
}

/** 입력값을 상한 안으로 자른다. 음수·소수·문자는 0으로. */
export function clampPoint(value, max) {
  const n = Math.floor(Number(value));
  if (!Number.isFinite(n) || n <= 0) return 0;
  return Math.min(n, max);
}
