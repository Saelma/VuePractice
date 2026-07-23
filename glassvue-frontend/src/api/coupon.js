import { apiGet, apiPost } from './client';

/**
 * 내 쿠폰 목록(미사용).
 *
 * `itemsTotal`(할인 전 상품합계)을 함께 보내면 서버가 쿠폰마다 **지금 얼마 깎이는지**(discountPreview)와
 * **쓸 수 있는지**(usable) + 못 쓰는 이유(reason)를 계산해 준다.
 * 할인 규칙(정액/정률·상한·최소주문금액)을 화면이 다시 구현하지 않기 위해서다.
 */
export function fetchMyCoupons(itemsTotal = 0) {
  return apiGet('/api/coupons/me', { itemsTotal });
}

/** 쿠폰 생성(관리자). 지금은 화면 없이 API만 — 필요해지면 관리 화면을 붙인다. */
export function createCoupon(payload) {
  return apiPost('/api/admin/coupons', payload);
}

/** 회원에게 발급(관리자). apiPost는 쿼리 파라미터를 안 받아 경로에 붙인다. */
export function issueCoupon(couponId, memberId) {
  return apiPost(`/api/admin/coupons/${couponId}/issue?memberId=${encodeURIComponent(memberId)}`);
}
