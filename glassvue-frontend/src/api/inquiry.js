import { apiGet, apiPost, apiPut, apiDelete } from './client';

// 상품 문의 목록. 응답: PageResponse<InquiryResponse>
// (비밀글은 서버가 작성자·관리자 외에는 content/answer를 마스킹해서 내려줌: masked=true)
export function fetchProductInquiries(productId, { page = 0, size = 5 } = {}) {
  return apiGet(`/api/products/${productId}/inquiries`, { page, size });
}

// 서버와 맞춘 첨부 이미지 최대 장수(백엔드 @Size(max=5)).
export const INQUIRY_IMAGE_MAX = 5;

export function createInquiry(productId, payload) {
  return apiPost(`/api/products/${productId}/inquiries`, payload); // { title, content, secret, imageIds }
}

export function updateInquiry(id, payload) {
  return apiPut(`/api/inquiries/${id}`, payload);
}

export function deleteInquiry(id) {
  return apiDelete(`/api/inquiries/${id}`);
}

export function answerInquiry(id, answer) {
  return apiPost(`/api/inquiries/${id}/answer`, { answer }); // 관리자 전용
}

export const INQUIRY_STATUS_TEXT = { WAITING: '답변대기', ANSWERED: '답변완료' };
export function inquiryStatusText(status) {
  return INQUIRY_STATUS_TEXT[status] || status;
}

// ─────────────────────────────────────────────────────────────────────────────
// 관리자 문의 관리 (2026-08-06, 백로그 G-3 1단계)
//
// 이것이 생기기 전까지 **관리자 문의 목록 API 는 0개**였다 — 관리자는 상품 상세에 들어가야만
// 문의에 답할 수 있었고, 그래서 상품과 무관한 일반 문의는 **넣어도 답할 경로가 없었다.**
//
// ⚠ **답변은 여기 없다** — `answerInquiry`(위)가 이미 상품 경로 밖에 있어 그대로 쓴다.
//    새로 는 것은 「무엇에 답할지 찾는 길」 하나뿐이다.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 관리자 문의 목록. 상품을 **가로질러** 전체를 본다(고객 목록은 상품별이다).
 *
 * ⚠ `status` 는 **세 가지 상태**다 — `null`(전체) · `'WAITING'` · `'ANSWERED'`.
 *    `apiGet` 이 `null` 파라미터를 빼므로 그대로 넘기면 된다.
 *    ⚠ 「전체」에 `'ALL'` 같은 문자열을 보내면 서버가 enum 변환에 실패해 **400** 이고,
 *    화면에는 그게 "문의가 없다" 로 보인다(관리자 주문·리뷰에서 두 번 겪은 자리).
 */
export function fetchAdminInquiries({ status = null, page = 0, size = 20, sort = null } = {}) {
  return apiGet('/api/admin/inquiries', { status, page, size, sort });
}

/**
 * 상태 필터 선택지.
 *
 * ⚠ 순서가 화면의 기본값을 정하지 않는다 — **기본은 「답변대기」**이고(관리자가 목록을 여는 이유가
 * *"답할 게 뭐가 남았나"* 라서), 그 선택은 화면이 한다. 서버는 안 보내면 전체를 준다.
 */
export const INQUIRY_STATUS_OPTIONS = [
  { value: 'WAITING', text: '답변대기' },
  { value: 'ANSWERED', text: '답변완료' },
  { value: null, text: '전체' },
];
