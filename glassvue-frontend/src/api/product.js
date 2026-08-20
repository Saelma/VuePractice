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

/**
 * 상품 삭제 — ⚠ **바로 지워지지 않는다**(2026-08-12, F-7).
 * 「삭제 대기」로 표시되고 유예 기간이 지나면 배치가 진짜로 지운다.
 * 그 사이 `/admin/products/trash` 에서 복구할 수 있다.
 */
export function deleteProduct(id) {
  return apiDelete(`/api/products/${id}`);
}

/** 삭제 대기 상품 목록 (관리자). 각 줄이 **언제 사라지는지**(`purgeAt`)를 서버에서 받아 온다. */
export function fetchDeletedProducts() {
  return apiGet('/api/admin/products/deleted');
}

/** 삭제 대기 복구 (관리자). 대기 중이 아니어도 200 이다(멱등). */
export function restoreProduct(id) {
  return apiPost(`/api/admin/products/${id}/restore`);
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
 * **기간 할인(타임세일) 중인가** — 서버가 준 `discountRate` 가 유일한 판정값이다 (2026-08-19, G-5).
 *
 * ⚠ **`price < regularPrice` 로 유추하지 않는다.** 1원짜리에 1% 를 걸면 반올림으로 두 값이 같아지는데,
 * 그때도 **세일 중인 것은 맞다**(배지·종료일이 떠야 한다). 그리고 「지금 세일인가」는
 * **서버 시계**로 판정해야 할 질문이다 — 브라우저 시계로 계산하면 자정 근처에서 갈린다
 * (B-26 에서 「오늘」을 서버가 준 것과 같은 이유).
 */
export function isOnSale(item) {
  return !!item && item.discountRate != null;
}

/**
 * 할인 중인가 — **세일 중**이거나, 정가가 있고 판매가보다 클 때.
 *
 * 서버는 `listPrice`(정가)만 내려주고 정가 기준 할인율은 화면이 계산한다. 택배사 조회 URL과 달리
 * 이건 **화면이 이미 가진 두 숫자의 산술**이라 서버가 완성해 줄 이유가 없다.
 * 대신 **여기 한 곳에만** 둬서 화면마다 계산이 갈리지 않게 한다(주문 상태 색을 한 곳에 모은 것과 같은 이유).
 *
 * ⚠ **주문 항목(OrderItemResponse)에는 `discountRate` 가 없다** — 스냅샷이라 산 시점의 «값» 만 있고
 * «지금 세일 중인가» 는 없다. **그게 맞다.**
 *
 * 🔴 **대신 2026-08-20(G-9)부터 `regularPrice`(세일 전 판매가)가 주문에도 실린다.** 그래서 주문 상세도
 * «세일로 샀다» 를 그릴 수 있다 — 그전에는 정가 칸이 빈 상품을 세일가로 사면 **화면에 흔적이 아예
 * 안 남았다**(실측 2026-08-20, `20260820-4733`).
 * ⚠ 여기서는 `regularPrice > price` 로 판정한다. 위 `isOnSale` 이 그 유추를 금지한 것과 **모순이 아니다** —
 * 거기는 «**지금** 세일 중인가» 라 서버 시계·반올림이 걸렸고, 여기는 «**그때** 깎여서 샀나» 라
 * 두 스냅샷의 대소가 곧 답이다. 반올림으로 같아지면 줄을 안 긋는데, 그건 안 그리는 게 맞다.
 */
export function hasDiscount(item) {
  if (!item) return false;
  return isOnSale(item)
    || (item.regularPrice != null && item.regularPrice > item.price)
    || (item.listPrice != null && item.listPrice > item.price);
}

/**
 * **취소선을 그을 값** — 없으면 `null`(그 줄을 아예 안 그린다).
 *
 * 🔴 **세일 중에는 「정가」가 아니라 「원래 판매가」를 긋는다**(2026-08-19, 사용자 결정).
 * 값이 셋(정가·판매가·세일가)인데 화면 자리는 둘이라, 고객이 **「지금 얼마나 싼가」**를
 * 바로 읽을 수 있는 쪽을 남겼다.
 *
 * ⚠ 세일인데 두 값이 같으면(1원짜리 1% 같은 경우) `null` 을 준다 — 같은 숫자에 줄을 그으면
 * **고장으로 보인다.** 배지는 그래도 뜬다(`hasDiscount` 는 참).
 */
export function strikePrice(item) {
  if (!item) return null;
  if (isOnSale(item)) {
    return item.regularPrice > item.price ? item.regularPrice : null;
  }
  // 주문 스냅샷(G-9) — `discountRate` 는 없고 `regularPrice` 만 있다. 세일로 샀으면 그 값을 긋는다.
  // ⚠ **정가보다 먼저 본다.** 세일과 정가가 둘 다 있으면 「지금 얼마나 싼가」를 보여주는 쪽이
  //    세일이다(위 isOnSale 갈래가 정가가 아니라 regularPrice 를 고른 것과 같은 판단).
  if (item.regularPrice != null && item.regularPrice > item.price) {
    return item.regularPrice;
  }
  return item.listPrice != null && item.listPrice > item.price ? item.listPrice : null;
}

/**
 * 할인율(%). 세일 중이면 **서버가 준 값을 그대로** 쓰고(화면이 다시 계산하지 않는다 —
 * 반올림 때문에 서버가 「20%」라 한 것이 화면에서 「19%」가 될 수 있다),
 * 아니면 정가 기준으로 계산한다. 표시용이라 반올림한다. 할인이 아니면 0.
 */
export function discountRate(item) {
  if (isOnSale(item)) return item.discountRate;
  if (!hasDiscount(item)) return 0;
  // 🔴 **긋는 값과 같은 기준으로 센다**(G-9). 취소선은 regularPrice 인데 비율은 listPrice 로 세면
  //    «12,000 → 9,600» 옆에 엉뚱한 %가 붙는다. 기준이 갈리면 화면이 스스로 모순된다.
  const base = strikePrice(item);
  if (!base) return 0;
  return Math.round(((base - item.price) / base) * 100);
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

// ── 기간 할인(타임세일) — 관리자 (2026-08-19, BACKLOG G-5) ──────────────

/**
 * 상품의 할인 일정 — 지난 것·진행 중·예정을 **시간순으로 전부**.
 *
 * 응답 한 줄: `{ id, rate, startDate, endDate, startsAt, endsAt, status }`
 *
 * ⚠ **`status` 를 화면이 계산하지 않는다**(`UPCOMING`·`ACTIVE`·`ENDED`). 「지금 진행 중인가」는
 * **서버 시계**로 답해야 할 질문이라, 브라우저 시계로 세면 자정 근처에서 갈린다
 * (B-26 에서 「오늘」을 서버가 준 것과 같은 이유).
 *
 * ⚠ **날짜는 `startDate`·`endDate` 를 쓴다**(`startsAt`·`endsAt` 이 아니라). 뒤엣것은
 * **배타 경계**라 종료일이 하루 뒤로 보인다.
 */
export function fetchProductDiscounts(productId) {
  return apiGet(`/api/admin/products/${productId}/discounts`);
}

/**
 * 할인 등록. `{ rate, startDate, endDate }` — **종료일은 포함**이고 경계는 서버가 만든다.
 *
 * ⚠ 기간이 겹치면 **400**(`PRODUCT-400DO`)이다. 경계가 **맞닿는 것은 겹침이 아니다** —
 * 「8/24 까지」와 「8/25 부터」는 이어 붙일 수 있다.
 */
export function createProductDiscount(productId, payload) {
  return apiPost(`/api/admin/products/${productId}/discounts`, payload);
}

/** 할인 수정. 겹침 검사에서 자기 자신은 빠지므로 기간을 그대로 두고 할인율만 고칠 수 있다. */
export function updateProductDiscount(productId, discountId, payload) {
  return apiPut(`/api/admin/products/${productId}/discounts/${discountId}`, payload);
}

/**
 * 할인 삭제 — **진행 중인 것도 지울 수 있다**(잘못 건 세일을 되돌리는 유일한 방법).
 * 지우면 그 순간 원가로 돌아가고, **이미 팔린 주문의 금액은 안 변한다**(B-7 스냅샷).
 */
export function deleteProductDiscount(productId, discountId) {
  return apiDelete(`/api/admin/products/${productId}/discounts/${discountId}`);
}

/** 할인 상태 표시. 서버 enum 에 값이 늘면 원문을 그대로 되돌린다(stockReasonText 와 같은 방식). */
export const DISCOUNT_STATUS_LABEL = {
  UPCOMING: '예정',
  ACTIVE: '진행 중',
  ENDED: '종료',
};
export function discountStatusText(status) {
  return DISCOUNT_STATUS_LABEL[status] || status || '';
}
