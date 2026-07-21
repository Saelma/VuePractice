import { apiGet, apiPost } from './client';

export function checkout() {
  return apiPost('/api/orders'); // 생성된 order id 반환
}

// 내 주문 목록(페이징). 응답: PageResponse<OrderResponse> { content, page, size, totalElements, ... }
export function fetchOrders({ status = null, page = 0, size = 10 } = {}) {
  return apiGet('/api/orders', { status, page, size });
}

// 관리자 전체 주문 목록. 사용자용과 경로가 다르고 응답에 구매자 정보(buyerNickname·summary)가 있다.
// 상태별 주문 건수(관리자 화면 상단 요약). { ORDERED: n, PAID: n, SHIPPED: n, CANCELLED: n }
export function fetchAdminOrderCounts() {
  return apiGet('/api/admin/orders/counts');
}

export function fetchAdminOrders({ status = null, buyer = null, page = 0, size = 10 } = {}) {
  return apiGet('/api/admin/orders', { status, buyer, page, size });
}

/** 상태 필터 SelectBox용 — '전체'는 value=null로 두어 파라미터가 빠지게 한다. */
export const ORDER_STATUS_OPTIONS = [
  { value: null, text: '전체' },
  { value: 'ORDERED', text: '결제대기' },
  { value: 'PAID', text: '결제완료' },
  { value: 'SHIPPED', text: '발송완료' },
  { value: 'CANCELLED', text: '취소됨' },
];

export function getOrder(id) {
  return apiGet(`/api/orders/${id}`);
}

export function payOrder(id) {
  return apiPost(`/api/orders/${id}/pay`); // ORDERED → PAID (실제 결제는 이후 PG 연동)
}

export function shipOrder(id) {
  return apiPost(`/api/orders/${id}/ship`); // PAID → SHIPPED (관리자)
}

export function cancelOrder(id) {
  return apiPost(`/api/orders/${id}/cancel`);
}

export const ORDER_STATUS_TEXT = {
  ORDERED: '결제대기',
  PAID: '결제완료',
  SHIPPED: '발송완료',
  CANCELLED: '취소됨',
};
export function orderStatusText(status) {
  return ORDER_STATUS_TEXT[status] || status;
}

// 상태별 배지 변형(DESIGN.md §5). 색을 직접 쓰지 않고 공용 `badge-*` 클래스를 돌려준다
// — 화면마다 매핑을 따로 두면 목록/상세/관리자에서 같은 상태가 다른 색으로 보인다(실제로 그랬다).
// 쓰는 쪽: <span class="badge" :class="orderStatusClass(status)">
const STATUS_CLASS = {
  ORDERED: 'badge-warning', // 결제대기 — 할 일이 남았다
  PAID: 'badge-success', // 결제완료
  SHIPPED: 'badge-neutral', // 발송완료 — 더 할 일 없음
  CANCELLED: 'badge-danger', // 취소됨
};
export function orderStatusClass(status) {
  return STATUS_CLASS[status] || 'badge-neutral';
}
