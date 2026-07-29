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

/**
 * 등급 정책 표 — [{ grade, minPurchase, earnPercent }]. **공개**(비로그인도 조회 가능).
 * `/api/points/me` 는 "내 등급"이라 로그인 전엔 못 쓴다. 홈의 혜택 안내가 "최대 N% 적립"을
 * 직접 적지 않게 하려고 정책 표를 따로 받는다 — 등급을 고치면 문구도 따라 바뀐다.
 *
 * ⚠ 경로가 `/api/points/**` 가 아니라 `/api/policy/**` 인 이유: 앞쪽은 SecurityConfig 가 한 줄로
 * 막는 보호 구역이라, 거기에 "이것만 공개" 예외를 넣으면 매처 순서에 의존하는 구멍이 된다.
 * 공개 정책은 처음부터 공개인 네임스페이스에 모은다(회원별 정보는 절대 안 들어간다).
 */
export function fetchGrades() {
  return apiGet('/api/policy/grades');
}

/** 정책 표에서 최고 적립률. 표가 비면 null — 화면은 그때 적립 안내를 통째로 감춘다. */
export function maxEarnPercent(grades) {
  const list = (grades ?? []).map((g) => g.earnPercent).filter((n) => typeof n === 'number');
  return list.length ? Math.max(...list) : null;
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
