import { apiGet, apiPost, apiDelete } from './client';
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

/**
 * 가입 쿠폰으로 지정/해제(관리자, V36).
 *
 * ⚠ **전체에서 한 장만** 지정된다 — 다른 쿠폰이 지정돼 있으면 서버가 자동으로 해제한다.
 * 설정(.env)이던 것을 데이터로 옮긴 자리라 **재시작 없이 즉시** 홈·가입 화면 문구에 반영된다.
 */
export function setWelcomeCoupon(couponId, welcome) {
  return welcome
    ? apiPost(`/api/admin/coupons/${couponId}/welcome`)
    : apiDelete(`/api/admin/coupons/${couponId}/welcome`);
}

/**
 * 오늘 그릴 이벤트 쿠폰 배너(G-8). **비로그인도 부를 수 있다.**
 *
 * ⚠ 배너를 그릴지는 **이 응답이 정한다** — 오늘 진행 중도 아니고 예정도 없으면 `null` 이 오고,
 * 그러면 자리를 아예 만들지 않는다("예정된 이벤트가 없습니다"는 자리만 먹는다).
 *
 * 응답이 말하는 것은 둘 중 하나다:
 * - `open: true`  — 오늘이 이벤트 날. `claimed` 로 「받기 / 받음」이 갈린다.
 * - `open: false` — 예고. `daysUntil`(D-n)만 알리고 **행동을 요구하지 않는다.**
 *
 * ⚠ `daysUntil` 은 **서버가 KST 로 센 값**이다. 화면에서 날짜를 다시 계산하지 말 것 —
 * 브라우저 시간대로 세면 어떤 사람에게만 D-1 이 D-2 로 보인다.
 */
export function fetchEventCoupon() {
  return apiGet('/api/coupons/event');
}

/**
 * 이벤트 쿠폰 「받기」. **회원당 한 장**이고 동시에 눌러도 한 장만 나간다(V49 유니크 인덱스).
 *
 * ⚠ 실패 코드 둘은 **성격이 다르다**:
 * - `COUPON-409I`(이미 받음) — 실패지만 **되돌릴 것이 없다.** 화면은 버튼을 「받음」으로 **확정**한다
 *   (에러 토스트가 아니다 — 다른 탭에서 이미 받았을 때 정확히 이 답이 온다).
 * - `COUPON-400C`(발급 창 닫힘) — 이벤트가 그 사이 끝났다. 배너를 다시 읽어 상태를 맞춘다.
 */
export function claimEventCoupon() {
  return apiPost('/api/coupons/event/claim');
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
