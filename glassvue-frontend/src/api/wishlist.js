import { apiGet, apiPost, apiDelete } from './client';

/**
 * 위시리스트(찜) (2026-07-24, 백로그 B-6).
 *
 * 장바구니와 API 모양이 비슷하지만 수량이 없다 — 찜은 "표시"일 뿐이라 회원·상품 한 쌍이면 끝난다.
 *
 * 추가·해제 둘 다 **멱등**이다(서버가 그렇게 답한다). 더블클릭이나 재시도로 같은 요청이 두 번 가도
 * 에러가 나지 않으므로, 화면은 실패 처리를 복잡하게 만들 필요가 없다.
 */

/** 내 찜 목록. 가격·재고·별점은 찜한 시점이 아니라 **지금** 값이다. */
export function fetchWishlist() {
  return apiGet('/api/wishlist');
}

/**
 * 내가 찜한 상품 id 목록.
 *
 * 상품 응답(`ProductResponse`)에 찜 여부가 안 들어 있는 이유는 catalog가 wishlist를 알게 되면
 * 도메인 순환이 되기 때문이다. 그래서 화면이 이 집합을 들고 있다가 하트를 채운다.
 */
export function fetchWishlistProductIds() {
  return apiGet('/api/wishlist/product-ids');
}

export function addWishlist(productId) {
  return apiPost(`/api/wishlist/${productId}`);
}

export function removeWishlist(productId) {
  return apiDelete(`/api/wishlist/${productId}`);
}
