import { apiGet } from './client';

/**
 * 상점 정책 조회 (2026-07-29) — 공개 API.
 *
 * ⚠ **화면이 정책 숫자를 갖지 않게 하려고** 만든 모듈이다. 장바구니·주문서는 서버가 계산해 준
 * `amountUntilFree` 를 쓰면 되지만, **비로그인 홈**은 장바구니가 없어 기준 금액 자체가 필요하다.
 * 여기서 안 받고 "3만원"이라고 적어 두면 application.yml 을 바꿨을 때 **안내 문구만 거짓말**이 된다.
 */

/** 배송비 정책 — { fee, freeThreshold }. freeThreshold 가 0이면 무료배송 정책 없음. */
export function fetchShippingPolicy() {
  return apiGet('/api/policy/shipping');
}
