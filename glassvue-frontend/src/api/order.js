import { apiGet, apiPost } from './client';

export function checkout() {
  return apiPost('/api/orders'); // 생성된 order id 반환
}

// 내 주문 목록(페이징). 응답: PageResponse<OrderResponse> { content, page, size, totalElements, ... }
export function fetchOrders({ status = null, page = 0, size = 10 } = {}) {
  return apiGet('/api/orders', { status, page, size });
}

// 관리자 전체 주문 목록. 사용자용과 경로가 다르고 응답에 구매자 정보(buyerNickname·summary)가 있다.
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

// 상태별 배지 색상(Tailwind)
const STATUS_CLASS = {
  ORDERED: 'bg-amber-50 text-amber-700',
  PAID: 'bg-blue-50 text-blue-600',
  SHIPPED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-slate-100 text-slate-500',
};
export function orderStatusClass(status) {
  return STATUS_CLASS[status] || 'bg-slate-100 text-slate-500';
}
