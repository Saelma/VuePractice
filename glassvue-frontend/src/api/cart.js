import { apiGet, apiPost, apiPatch, apiDelete } from './client';

/**
 * 장바구니 (2026-07-24 C-8): 담기는 단위가 상품 → **옵션(variant)** 으로 바뀌었다.
 * 서버 Redis field 도 variantId 라, 담기·수량·삭제 모두 variantId 로 한다.
 */

export function getCart() {
  return apiGet('/api/cart');
}

export function addToCart(variantId, quantity) {
  return apiPost('/api/cart/items', { variantId, quantity });
}

export function updateCartItem(variantId, quantity) {
  return apiPatch(`/api/cart/items/${variantId}`, { quantity });
}

export function removeCartItem(variantId) {
  return apiDelete(`/api/cart/items/${variantId}`);
}

export function clearCart() {
  return apiDelete('/api/cart');
}
