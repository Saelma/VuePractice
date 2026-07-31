import { apiGet, apiPost } from './client';
import { priceText } from './product';

/**
 * 내 쿠폰 목록(미사용).
 *
 * `itemsTotal`(할인 전 상품합계)을 함께 보내면 서버가 쿠폰마다 **지금 얼마 깎이는지**(discountPreview)와
 * **쓸 수 있는지**(usable) + 못 쓰는 이유(reason)를 계산해 준다.
 * 할인 규칙(정액/정률·상한·최소주문금액)을 화면이 다시 구현하지 않기 위해서다.
 *
 * 쿠폰함(혜택 허브)은 주문 맥락이 없어 itemsTotal=0 으로 부른다 — 이때 usable/discountPreview 는
 * 의미가 없고(주문 전이라), 쿠폰의 **본질 속성**(할인·최소주문·만료일)만 보여준다.
 */
export function fetchMyCoupons(itemsTotal = 0) {
  return apiGet('/api/coupons/me', { itemsTotal });
}

/**
 * 가입 즉시 자동 발급되는 쿠폰(G-2). **비로그인도 부를 수 있다.**
 *
 * ⚠ 화면이 "가입하면 쿠폰" 문구를 띄울지는 **이 응답이 정한다** — 기능이 꺼져 있거나 설정된 쿠폰이
 * 지워졌으면 `null` 이 오고, 그러면 문구를 감춘다. 혜택을 화면에 하드코딩하면 설정만 바뀌어도
 * **안내가 거짓말이 된다**(HomeView 혜택 스트립의 원칙).
 */
export function fetchWelcomeCoupon() {
  return apiGet('/api/coupons/welcome');
}

/** 쿠폰 할인 표기 — 정액(FIXED)은 금액, 정률(PERCENT)은 %. */
export function couponDiscountText(c) {
  return c.discountType === 'PERCENT' ? `${c.discountValue}% 할인` : `${priceText(c.discountValue)} 할인`;
}

/** 쿠폰 정의 목록(관리자). 최신 생성순. */
export function fetchAdminCoupons({ page = 0, size = 50 } = {}) {
  return apiGet('/api/admin/coupons', { page, size });
}

export const DISCOUNT_TYPE_LABEL = { FIXED: '정액(원)', PERCENT: '정률(%)' };

/** 쿠폰 생성(관리자). */
export function createCoupon(payload) {
  return apiPost('/api/admin/coupons', payload);
}

/** 회원에게 발급(관리자). apiPost는 쿼리 파라미터를 안 받아 경로에 붙인다. */
export function issueCoupon(couponId, memberId) {
  return apiPost(`/api/admin/coupons/${couponId}/issue?memberId=${encodeURIComponent(memberId)}`);
}
