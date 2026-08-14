import { apiGet } from './client';

// --- 관리자 감사 이력 (SUPER_ADMIN 전용) ---
// 누가(actor) 누구를(target) 언제 어떻게 조작했는지의 append-only 이력. 조회 권한은 서버가
// /api/admin/audit/** = SUPER_ADMIN 으로 막는다(일반 ADMIN 은 403). 화면 진입도 라우터가 SUPER 만 통과.

/** 감사 이력 목록. action(조작 종류)·targetLogin(대상 loginId 부분일치)로 좁힐 수 있고, 정렬 미지정 시 최신순. */
export function fetchAuditLogs({ action = null, targetLogin = null, page = 0, size = 20 } = {}) {
  return apiGet('/api/admin/audit', { action, targetLogin, page, size });
}

/**
 * 조작 종류 표시 문구.
 *
 * 🔴 **백엔드 `AuditAction` enum 과 키가 정확히 같아야 한다.** 어긋나면 두 가지가 조용히 깨진다:
 * ① 필터 드롭다운이 이 객체로 만들어져 **빠진 종류를 골라 볼 방법이 없다**
 * ② 목록에 `ORDER_CANCEL` 같은 **날문자**가 그대로 뜬다(아래 fallback).
 *
 * ⚠ **실제로 어긋나 있었다**(2026-08-10 발견): `MEMBER_DELETE` 가 2026-07-30(B-24)에 추가됐는데
 * 여기 안 들어와 **11일간** 빠져 있었고, 그 사이 5개가 더 늘어 **9개 중 3개만** 있었다.
 * 감사를 남기는 쪽과 보는 쪽이 짝인데 한쪽만 자란 것이다(WA §1-2-1).
 *
 * → **`audit.test.js` 가 `AuditAction.java` 를 읽어 키 집합을 대조한다.** 여기 손대지 않고
 *   enum 만 늘리면 **테스트가 빨개진다.** 라벨을 채우는 것은 재발 방지가 아니라 그때의 수습이다.
 */
export const AUDIT_ACTION_LABEL = {
  MEMBER_SUSPEND: '회원 정지',
  MEMBER_UNSUSPEND: '정지 해제',
  MEMBER_ROLE_CHANGE: '역할 변경',
  MEMBER_DELETE: '회원 강제 삭제',
  ORDER_CANCEL: '주문 취소(대행)',
  REVIEW_HIDE: '리뷰 숨김',
  REVIEW_UNHIDE: '리뷰 숨김 해제',
  INQUIRY_HIDE: '문의 숨김',
  INQUIRY_UNHIDE: '문의 숨김 해제',
  PRODUCT_DELETE: '상품 삭제',
  PRODUCT_RESTORE: '상품 복구',
};
export function auditActionText(action) {
  return AUDIT_ACTION_LABEL[action] || action || '';
}

/**
 * 조작 종류별 뱃지 색 — **되돌릴 수 있느냐로 가른다.**
 *
 * - `danger` **되돌릴 수 없다** — 회원 강제 삭제 · 주문 취소(대행)
 * - `warning` **막거나 내린다**(되돌릴 수 있다) — 회원 정지 · 리뷰/문의 숨김
 * - `success` **되돌리는 조작** — 정지 해제 · 숨김 해제
 * - `neutral` 그 밖 — 역할 변경
 *
 * ⚠ **`MEMBER_SUSPEND` 를 danger 에서 warning 으로 내렸다**(2026-08-10). 종류가 3개일 땐
 * 「정지=가장 위험」이 맞았지만 9개가 되면서 **되돌릴 수 없는 것**(삭제·취소)과 같은 색을 쓸 수 없다.
 * 정지는 해제가 있고 삭제는 없다 — 그 차이가 색으로 보여야 한다.
 *
 * ⚠ 이 맵도 **enum 과 키가 같아야 한다** — 테스트가 라벨과 함께 대조한다.
 * 빠지면 조용히 회색으로 떨어져 «위험한 조작이 눈에 안 띄는» 상태가 된다.
 */
export const AUDIT_ACTION_BADGE = {
  MEMBER_SUSPEND: 'badge-warning',
  MEMBER_UNSUSPEND: 'badge-success',
  MEMBER_ROLE_CHANGE: 'badge-neutral',
  MEMBER_DELETE: 'badge-danger',
  ORDER_CANCEL: 'badge-danger',
  REVIEW_HIDE: 'badge-warning',
  REVIEW_UNHIDE: 'badge-success',
  INQUIRY_HIDE: 'badge-warning',
  INQUIRY_UNHIDE: 'badge-success',
  // 상품 삭제는 **유예 안에서는 되돌릴 수 있다**(F-7) — 그래서 danger 가 아니라 warning 이다.
  // ⚠ 유예가 지나 배치가 진짜로 지우는 순간은 되돌릴 수 없지만, 그건 감사에 안 남는다(V50 참조).
  PRODUCT_DELETE: 'badge-warning',
  PRODUCT_RESTORE: 'badge-success',
};
export function auditActionBadge(action) {
  return AUDIT_ACTION_BADGE[action] || 'badge-neutral';
}
