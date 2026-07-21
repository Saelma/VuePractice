import { apiGet, apiPost, apiPut, apiDelete } from './client';

export function fetchProducts({ name, categoryId, minPrice, maxPrice, status, sort, page = 0, size = 10 } = {}) {
  // sort는 Spring Pageable 형식("price,asc"). 백엔드가 화이트리스트로 거르므로 임의 필드는 400이 된다.
  return apiGet('/api/products', { name, categoryId, minPrice, maxPrice, status, sort, page, size });
}

/**
 * 상품 목록 정렬 옵션 — 백엔드 SORTABLE 화이트리스트(createdAt·price·stock·name·avgRating)와 맞춰야 한다.
 * 여기 없는 값을 보내면 서버가 400으로 거부한다.
 */
export const SORT_OPTIONS = [
  { value: 'createdAt,desc', text: '최신순' },
  { value: 'price,asc', text: '가격 낮은순' },
  { value: 'price,desc', text: '가격 높은순' },
  { value: 'avgRating,desc', text: '평점 높은순' },
  { value: 'name,asc', text: '이름순' },
];

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
