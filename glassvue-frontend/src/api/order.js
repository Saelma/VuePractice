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

export function cancelOrder(id) {
  return apiPost(`/api/orders/${id}/cancel`);
}

export const ORDER_STATUS_TEXT = { ORDERED: '주문완료', CANCELLED: '취소됨' };
export function orderStatusText(status) {
  return ORDER_STATUS_TEXT[status] || status;
}
