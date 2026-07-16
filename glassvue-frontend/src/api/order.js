import { apiGet, apiPost } from './client';

export function checkout() {
  return apiPost('/api/orders'); // 생성된 order id 반환
}

export function fetchOrders() {
  return apiGet('/api/orders');
}

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
