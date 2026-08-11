import { apiGet, apiPost, apiPut, apiDelete } from './client';

export function fetchProducts({ name, categoryId, minPrice, maxPrice, status, sort, page = 0, size = 10 } = {}) {
  // sort는 Spring Pageable 형식("price,asc"). 백엔드가 화이트리스트로 거르므로 임의 필드는 400이 된다.
  return apiGet('/api/products', { name, categoryId, minPrice, maxPrice, status, sort, page, size });
}

/**
 * 상품 목록 정렬 옵션 — 백엔드 화이트리스트(`ProductRepositoryImpl.SORTABLE`)에 있는 값만 쓴다.
 * 여기 없는 값을 보내면 서버가 400으로 거부한다.
 *
 * ⚠ **화이트리스트 목록을 여기 옮겨 적지 않는다**(2026-08-11). 예전엔
 * `(createdAt·price·stock·name·avgRating·soldCount)` 라고 적어 뒀는데 `reviewCount` 가 추가됐을 때
 * **주석만 안 자랐다** — 아래 옵션에는 이미 `reviewCount,desc` 가 있어서, 주석이 옵션보다 낡은 상태였다.
 * 복사본은 원본이 자랄 때 따라오지 않는다. **원본이 어디인지만 가리킨다.**
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

/**
 * 재고 부족 옵션 — 관리자 대시보드용 (2026-08-03, 백로그 B-16).
 *
 * 응답: `{ threshold, count, items: [{ productId, productName, variantName, stock }] }`
 *
 * ⚠ **기준값(`threshold`)을 화면이 적지 않는다.** 서버 설정(`catalog.low-stock-threshold`)이
 * 판정 기준이고 재고 부족 알림도 같은 값을 쓴다 — 화면에 "5개 이하"를 박아 두면 설정을 바꾼 순간
 * 문구가 거짓말이 된다(혜택 문구를 서버가 줄 때만 노출하기로 한 것과 같은 자리, DESIGN §7).
 *
 * ⚠ `items` 는 상위 몇 줄이라 **`count` 보다 짧을 수 있다.** 숫자는 `count` 를 쓴다.
 */
export function fetchLowStock() {
  return apiGet('/api/admin/products/low-stock');
}

/**
 * 상품의 재고 변경 이력 — 관리자 (2026-08-04, 백로그 B-19).
 *
 * 응답: `PageResponse<{ id, variantName, reason, quantity, stockAfter, orderId, actorName, createdAt }>`
 *
 * ⚠ **기준은 옵션 id 가 아니라 상품 + 옵션명**이다 — 관리자가 상품을 저장하면 옵션이 통째로
 * 교체돼 옵션 id 가 바뀌기 때문이다. 그래서 **지금 옵션 목록에 없는 이름**이 나올 수 있다
 * (삭제된 옵션의 과거 이력) — 화면이 "모르는 옵션"으로 취급해 감추면 안 된다.
 *
 * ⚠ **합계로 현재 재고를 검산하지 않는다.** V39 이전 변동은 기록이 없어(백필 안 함) 오래된 상품은
 * 합계가 현재 재고와 다르다.
 */
export function fetchStockHistory(productId, { page = 0, size = 20 } = {}) {
  return apiGet(`/api/admin/products/${productId}/stock-history`, { page, size });
}

/**
 * 재고 변동 사유 표시.
 *
 * ⚠ 서버 enum 에 값이 늘면 여기 없는 키가 온다 — 그때 빈칸이 되지 않게 원문을 그대로 되돌린다
 * (`auditActionText` 와 같은 방식).
 */
export const STOCK_REASON_LABEL = {
  ORDER: '주문',
  CANCEL: '주문 취소',
  RETURN: '반품 승인',
  ADMIN_CREATE: '등록',
  ADMIN_EDIT: '관리자 편집',
};
export function stockReasonText(reason) {
  return STOCK_REASON_LABEL[reason] || reason || '';
}

/** 변동량 표시 — 부호를 **항상** 붙인다. `+3`/`-3` 이라 0 이 될 일은 없다(변동 0은 기록되지 않는다). */
export function stockDeltaText(quantity) {
  const n = Number(quantity);
  return (n > 0 ? '+' : '') + n.toLocaleString('ko-KR');
}
