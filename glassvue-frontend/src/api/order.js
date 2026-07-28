import { apiGet, apiPost } from './client';

// 배송지는 주문 시점 스냅샷이라 요청 본문으로 받는다(서버가 회원 기본 배송지를 읽지 않는다).
// 품목은 장바구니에서 서버가 읽으므로 본문에 없다 — 가격 위변조 방지.
export function checkout(address) {
  return apiPost('/api/orders', address); // 생성된 order id 반환
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

export function fetchAdminOrders({ status = null, buyer = null, orderNo = null, page = 0, size = 10 } = {}) {
  return apiGet('/api/admin/orders', { status, buyer, orderNo, page, size });
}

// 특정 회원의 주문 목록(관리자, B-11 회원 상세). status=RETURN_REQUESTED/RETURNED 로 반품만 추린다.
export function fetchAdminMemberOrders(memberId, { status = null, page = 0, size = 10 } = {}) {
  return apiGet(`/api/admin/orders/by-member/${memberId}`, { status, page, size });
}

/** 상태 필터 SelectBox용 — '전체'는 value=null로 두어 파라미터가 빠지게 한다. */
export const ORDER_STATUS_OPTIONS = [
  { value: null, text: '전체' },
  { value: 'ORDERED', text: '결제대기' },
  { value: 'PAID', text: '결제완료' },
  { value: 'SHIPPED', text: '발송완료' },
  { value: 'DELIVERED', text: '배송완료' },
  { value: 'CANCELLED', text: '취소됨' },
  { value: 'RETURN_REQUESTED', text: '반품요청' },
  { value: 'RETURNED', text: '반품완료' },
];

/**
 * 택배사 선택지(관리자 발송 처리용).
 *
 * ⚠ 백엔드 `DeliveryCarrier` enum과 값이 같아야 한다 — 여기 없는 값을 보내면 서버가 400으로 거른다.
 * 목록을 API로 내려받지 않고 정적으로 둔 이유: 선택지 표시는 화면의 몫이고, 실제 검증과
 * **조회 URL 생성은 서버가** 한다(화면은 응답의 `trackingUrl`을 링크로 걸 뿐이다).
 * 그래서 이 목록이 낡아도 생기는 일은 "선택지가 하나 안 보인다"이지 잘못된 저장이 아니다.
 */
export const DELIVERY_CARRIERS = [
  { value: 'CJ', text: 'CJ대한통운' },
  { value: 'KOREA_POST', text: '우체국택배' },
  { value: 'HANJIN', text: '한진택배' },
  { value: 'LOTTE', text: '롯데택배' },
  { value: 'LOGEN', text: '로젠택배' },
  { value: 'ETC', text: '기타(직접 전달 등)' },
];

export function getOrder(id) {
  return apiGet(`/api/orders/${id}`);
}

export function payOrder(id) {
  return apiPost(`/api/orders/${id}/pay`); // ORDERED → PAID (실제 결제는 이후 PG 연동)
}

/**
 * 발송 처리(관리자, PAID → SHIPPED). 운송장이 **필수**다.
 * 운송장 없이 발송하면 고객이 추적할 수 없고 나중에 채워 넣을 경로도 없어, 서버가 본문을 요구한다.
 */
export function shipOrder(id, { carrier, trackingNo }) {
  return apiPost(`/api/orders/${id}/ship`, { carrier, trackingNo });
}

/** 배송완료 처리(관리자, SHIPPED → DELIVERED). 지금은 수동 전이 — 택배사 웹훅 연동은 이후 단계. */
export function deliverOrder(id) {
  return apiPost(`/api/orders/${id}/deliver`);
}

export function cancelOrder(id) {
  return apiPost(`/api/orders/${id}/cancel`);
}

/** 반품 요청(본인, DELIVERED만). 사유 필수. 승인 시 재고 복원 + 적립금 환불(2026-07-24 C-9). */
export function requestReturn(id, reason) {
  return apiPost(`/api/orders/${id}/return-request`, { reason });
}

export function approveReturn(id) {
  return apiPost(`/api/orders/${id}/return-approve`);
}

export function rejectReturn(id) {
  return apiPost(`/api/orders/${id}/return-reject`);
}

export const ORDER_STATUS_TEXT = {
  ORDERED: '결제대기',
  PAID: '결제완료',
  SHIPPED: '발송완료',
  DELIVERED: '배송완료',
  CANCELLED: '취소됨',
  RETURN_REQUESTED: '반품요청',
  RETURNED: '반품완료',
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
  SHIPPED: 'badge-neutral', // 발송완료 — 배송 중(아직 종착이 아니다)
  DELIVERED: 'badge-success', // 배송완료 — 정상 종료
  CANCELLED: 'badge-danger', // 취소됨
  RETURN_REQUESTED: 'badge-warning', // 반품요청 — 관리자 처리 대기(할 일 있음)
  RETURNED: 'badge-danger', // 반품완료 — 정상 판매가 아님
};
export function orderStatusClass(status) {
  return STATUS_CLASS[status] || 'badge-neutral';
}
