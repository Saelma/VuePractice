import { apiGet, apiPost, apiPut, apiDelete } from './client';

export function fetchProducts({ name, categoryId, minPrice, maxPrice, status, page = 0, size = 10 } = {}) {
  return apiGet('/api/products', { name, categoryId, minPrice, maxPrice, status, page, size });
}

export function getProduct(id) {
  return apiGet(`/api/products/${id}`);
}

export function createProduct(payload) {
  return apiPost('/api/products', payload);
}

export function updateProduct(id, payload) {
  return apiPut(`/api/products/${id}`, payload);
}

export function deleteProduct(id) {
  return apiDelete(`/api/products/${id}`);
}

// 공통: 상태 표시
export const STATUS_OPTIONS = [
  { value: 'SELLING', text: '판매중' },
  { value: 'SOLD_OUT', text: '품절' },
  { value: 'HIDDEN', text: '숨김' },
];
export function statusText(status) {
  return STATUS_OPTIONS.find((s) => s.value === status)?.text || status;
}
export function priceText(price) {
  return price != null ? Number(price).toLocaleString('ko-KR') + '원' : '';
}
