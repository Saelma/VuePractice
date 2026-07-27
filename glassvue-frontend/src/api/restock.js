import { apiGet, apiPost, apiDelete } from './client';

/**
 * 재입고 알림 신청 (백로그 B-9).
 *
 * 위시리스트와 API 모양이 같다 — 회원·상품 한 쌍. 신청·취소 둘 다 **멱등**이라(서버가 그렇게 답한다)
 * 더블클릭·재시도로 같은 요청이 두 번 가도 에러가 없다.
 *
 * 단위가 옵션이 아니라 **상품**인 이유: 관리자 상품 편집이 옵션 id 를 매번 새로 만들어 옵션 단위로는
 * 신청을 안정적으로 이을 수 없다. 그래서 상품 전체가 품절일 때 상품에 신청하고, 상품이 다시
 * 들어오면(총재고 0→양수) 알림이 온다.
 */

/**
 * 내가 재입고 신청한 상품 id 목록.
 *
 * 상품 응답에 신청 여부가 없는 이유는 catalog 가 restock 을 알게 되면 도메인 순환이 되기 때문이다
 * (위시리스트와 같다). 화면이 이 집합을 들고 버튼 상태를 채운다.
 */
export function fetchRestockProductIds() {
  return apiGet('/api/restock/product-ids');
}

export function subscribeRestock(productId) {
  return apiPost(`/api/restock/${productId}`);
}

export function unsubscribeRestock(productId) {
  return apiDelete(`/api/restock/${productId}`);
}
