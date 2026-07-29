import { apiGet, apiPost, apiPut, apiDelete } from './client';

export function fetchProducts({ name, categoryId, minPrice, maxPrice, status, sort, page = 0, size = 10 } = {}) {
  // sort는 Spring Pageable 형식("price,asc"). 백엔드가 화이트리스트로 거르므로 임의 필드는 400이 된다.
  return apiGet('/api/products', { name, categoryId, minPrice, maxPrice, status, sort, page, size });
}

/**
 * 상품 목록 정렬 옵션 — 백엔드 SORTABLE 화이트리스트(createdAt·price·stock·name·avgRating·soldCount)와 맞춰야 한다.
 * 여기 없는 값을 보내면 서버가 400으로 거부한다.
 */
export const SORT_OPTIONS = [
  { value: 'createdAt,desc', text: '최신순' },
  { value: 'soldCount,desc', text: '인기순' },
  { value: 'price,asc', text: '가격 낮은순' },
  { value: 'price,desc', text: '가격 높은순' },
  { value: 'avgRating,desc', text: '평점 높은순' },
  // 평점 바로 뒤에 둔다 — 둘은 짝이다. 평점만 보면 "리뷰 1건에 별 5개"가 위로 오는데,
  // 리뷰 많은순은 "많이 검증된 것"을 보여줘 그 왜곡을 보완한다(8fter 의 「사용후기」 정렬).
  { value: 'reviewCount,desc', text: '리뷰 많은순' },
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

/**
 * 할인 중인가 — 정가가 있고 판매가보다 클 때만.
 *
 * 서버는 `listPrice`(정가)만 내려주고 할인율은 화면이 계산한다. 택배사 조회 URL과 달리
 * 이건 **화면이 이미 가진 두 숫자의 산술**이라 서버가 완성해 줄 이유가 없다.
 * 대신 **여기 한 곳에만** 둬서 화면마다 계산이 갈리지 않게 한다(주문 상태 색을 한 곳에 모은 것과 같은 이유).
 */
export function hasDiscount(item) {
  return !!item && item.listPrice != null && item.listPrice > item.price;
}

/** 할인율(%). 표시용이라 반올림한다. 할인이 아니면 0. */
export function discountRate(item) {
  if (!hasDiscount(item)) return 0;
  return Math.round(((item.listPrice - item.price) / item.listPrice) * 100);
}
