import { apiGet, apiPost, apiPatch, apiDelete } from './client';

export function getCart() {
  return apiGet('/api/cart');
}

export function addToCart(productId, quantity) {
  return apiPost('/api/cart/items', { productId, quantity });
}

export function updateCartItem(productId, quantity) {
  return apiPatch(`/api/cart/items/${productId}`, { quantity });
}

export function removeCartItem(productId) {
  return apiDelete(`/api/cart/items/${productId}`);
}

export function clearCart() {
  return apiDelete('/api/cart');
}
