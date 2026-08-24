import { apiGet } from './client';

// --- 관리자 감사 이력 (SUPER_ADMIN 전용) ---
// 누가(actor) 누구를(target) 언제 어떻게 조작했는지의 append-only 이력. 조회 권한은 서버가
// /api/admin/audit/** = SUPER_ADMIN 으로 막는다(일반 ADMIN 은 403). 화면 진입도 라우터가 SUPER 만 통과.

/**
 * 감사 이력 목록. action(조작 종류)·targetType(대상 종류)·targetLogin(대상 loginId 부분일치)로
 * 좁힐 수 있고, 정렬 미지정 시 최신순.
 *
 * 🔴 **targetType 은 회원 아닌 행을 좁히는 유일한 수단이다**(2026-08-20, V53). 상품·쿠폰 행은
 * targetLogin 이 비어 있어 「대상 아이디」로 못 찾는다 — 그전에는 「조작 종류」를 하나씩
 * 골라 보는 수밖에 없었다.
 */
export function fetchAuditLogs({ action = null, targetType = null, targetLogin = null,
  page = 0, size = 20 } = {}) {
  return apiGet('/api/admin/audit', { action, targetType, targetLogin, page, size });
}

/**
 * 대상 종류 표시 문구 (2026-08-20, V53).
 *
 * ⚠ **`DISCOUNT` 가 없는 것이 맞다** — 세일 조작의 대상은 «상품» 이다. 할인 id 는 사람에게
 * 의미가 없고, 대상을 상품으로 잡아야 상품 등록·수정·삭제와 **같은 줄에서** 읽힌다.
 */
export const AUDIT_TARGET_TYPE_LABEL = {
  MEMBER: '회원',
  PRODUCT: '상품',
  COUPON: '쿠폰',
  // 🔴 **여기가 V53 틀의 첫 확장이다** (2026-08-21, V56). 카테고리·공지는 위 셋 어디에도 안 들어간다 —
  //    상품으로 접으면 「대상 종류=상품」에 상품 아닌 행이 섞여, 이 필터의 쓸모를 스스로 무너뜨린다.
  CATEGORY: '카테고리',
  NOTICE: '공지',
};
export function auditTargetTypeText(targetType) {
  return AUDIT_TARGET_TYPE_LABEL[targetType] || targetType || '';
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
  // 🔴 전체 취소와 **갈라 적는다** — 돈이 다르게 움직인다(G-4). 전체 취소는 결제금액 전부가
  //    돌아가고 쿠폰도 복구되지만, 부분 취소는 몫만 돌려주고 **쿠폰은 그대로 걸려 있다.**
  ORDER_ITEM_CANCEL: '부분 취소(대행)',
  ORDER_SHIP: '발송 처리',
  ORDER_DELIVER: '배송완료 처리',
  ORDER_RETURN_APPROVE: '반품 승인',
  ORDER_RETURN_REJECT: '반품 거절',
  REVIEW_HIDE: '리뷰 숨김',
  REVIEW_UNHIDE: '리뷰 숨김 해제',
  INQUIRY_HIDE: '문의 숨김',
  INQUIRY_UNHIDE: '문의 숨김 해제',
  PRODUCT_DELETE: '상품 삭제',
  PRODUCT_RESTORE: '상품 복구',
  PRODUCT_CREATE: '상품 등록',
  PRODUCT_UPDATE: '상품 수정',
  COUPON_CREATE: '쿠폰 등록',
  COUPON_ISSUE: '쿠폰 발급',
  COUPON_WELCOME_SET: '가입 쿠폰 지정',
  DISCOUNT_CREATE: '세일 등록',
  DISCOUNT_UPDATE: '세일 수정',
  DISCOUNT_DELETE: '세일 삭제',
  CATEGORY_CREATE: '카테고리 등록',
  CATEGORY_DELETE: '카테고리 삭제',
  NOTICE_CREATE: '공지 등록',
  NOTICE_UPDATE: '공지 수정',
  NOTICE_DELETE: '공지 삭제',
  INQUIRY_ANSWER: '문의 답변',
};
export function auditActionText(action) {
  return AUDIT_ACTION_LABEL[action] || action || '';
}

/**
 * 조작 종류별 뱃지 색 — **되돌릴 수 있느냐로 가른다.**
 *
 * - `danger` **되돌릴 수 없다** — 회원 강제 삭제 · 주문 취소(대행) · 반품 승인
 * - `warning` **막거나 내린다 · 뒤집는다**(되돌릴 수 있다) — 회원 정지 · 리뷰/문의 숨김 · 반품 거절
 * - `success` **되돌리는 조작** — 정지 해제 · 숨김 해제 · 상품 복구
 * - `neutral` 그 밖 — 역할 변경 · **정상 흐름의 진행**(발송 · 배송완료)
 *
 * 🔴 **발송·배송완료를 neutral 로 둔 것은 판단이다**(2026-08-14). 배송완료는 **적립금이 나가므로**
 * 「되돌릴 수 없다」에 걸릴 여지가 있다. 그래도 neutral 인 이유: 이 색의 쓸모는 **목록에서 멈칫하게
 * 만드는 것**인데, 발송·배송완료는 **모든 주문이 거치는 정상 진행**이라 danger 로 칠하면
 * 원장의 절반이 빨개진다 — 그러면 **진짜 위험한 줄이 묻힌다.**
 * ⚠ 대신 나간 적립금은 「내용」에 숫자로 적힌다(V51) — 색이 아니라 값으로 읽게 했다.
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
  // 부분 취소도 **돈이 나간다** — 전체보다 작을 뿐이라 무게를 낮추지 않는다.
  ORDER_ITEM_CANCEL: 'badge-danger',
  ORDER_SHIP: 'badge-neutral',
  ORDER_DELIVER: 'badge-neutral',
  // 반품 승인은 **환불이 나간다** — 취소와 같은 무게다.
  ORDER_RETURN_APPROVE: 'badge-danger',
  // 거절은 **고객의 요청을 뒤집는** 것이지 끝내는 것이 아니다 — 다시 요청할 수 있다.
  ORDER_RETURN_REJECT: 'badge-warning',
  REVIEW_HIDE: 'badge-warning',
  REVIEW_UNHIDE: 'badge-success',
  INQUIRY_HIDE: 'badge-warning',
  INQUIRY_UNHIDE: 'badge-success',
  // 상품 삭제는 **유예 안에서는 되돌릴 수 있다**(F-7) — 그래서 danger 가 아니라 warning 이다.
  // ⚠ 유예가 지나 배치가 진짜로 지우는 순간은 되돌릴 수 없지만, 그건 감사에 안 남는다(V50 참조).
  PRODUCT_DELETE: 'badge-warning',
  PRODUCT_RESTORE: 'badge-success',
  // 등록·수정은 **정상 흐름의 진행**이다 — 발송·배송완료를 neutral 로 둔 것과 같은 판단.
  // ⚠ 상품 수정은 빈도가 가장 높아, 색을 주면 원장이 그 색으로 덮인다.
  PRODUCT_CREATE: 'badge-neutral',
  PRODUCT_UPDATE: 'badge-neutral',
  COUPON_CREATE: 'badge-neutral',
  // 🔴 **회수할 방법이 없다** — 발급 취소 API 자체가 없다(쿠폰을 «되돌리는» 경로는 주문 취소뿐).
  //    되돌릴 수 없다는 기준에 그대로 걸린다. ⚠ 빈도가 낮아(수동 발급) 원장을 덮지 않는다.
  COUPON_ISSUE: 'badge-danger',
  // 지정/해제가 한 값이라 색이 하나다 — 토글이고 둘 다 되돌릴 수 있다.
  COUPON_WELCOME_SET: 'badge-neutral',
  // 🔴 **세 값 다 warning 이다.** 세일은 등록·수정·삭제가 전부 «가격이 움직인다» 이고,
  //    어느 방향이 고객에게 유리한지가 그때그때 달라 **색으로는 못 가른다**
  //    (진행 중인 세일을 지우면 값이 오르고, 거는 것은 내린다).
  //    ⚠ 얼마가 어떻게 움직였는지는 「내용」에 %와 기간으로 적힌다 — 색이 아니라 값으로 읽는다.
  DISCOUNT_CREATE: 'badge-warning',
  DISCOUNT_UPDATE: 'badge-warning',
  DISCOUNT_DELETE: 'badge-warning',
  CATEGORY_CREATE: 'badge-neutral',
  // 🔴 **되돌릴 수 없다** — 카테고리에는 유예(F-7)가 없어 행이 진짜로 사라진다. 같은 이름으로
  //    다시 만들어도 **id 가 달라** 예전 것이 아니다. 상품 삭제가 warning 인 것과 갈리는 지점이다.
  CATEGORY_DELETE: 'badge-danger',
  NOTICE_CREATE: 'badge-neutral',
  NOTICE_UPDATE: 'badge-neutral',
  // 공지도 soft delete 가 없다 — 지우면 끝이다.
  NOTICE_DELETE: 'badge-danger',
  // 🔴 **답변 자체는 고칠 수 있는데 «첫 답변» 은 알림을 내보낸다 — 그건 회수할 수 없다.**
  //    다만 danger 로 칠하지 않는다: 문의에 답하는 것은 **정상 흐름의 진행**이고 빈도가 높아,
  //    빨갛게 칠하면 원장이 그 색으로 덮여 **진짜 위험한 줄이 묻힌다**(발송·배송완료와 같은 판단).
  //    ⚠ 대신 알림이 나갔는지는 「내용」에 «첫 답변» 으로 적힌다 — 색이 아니라 값으로 읽는다.
  INQUIRY_ANSWER: 'badge-neutral',
};
export function auditActionBadge(action) {
  return AUDIT_ACTION_BADGE[action] || 'badge-neutral';
}
