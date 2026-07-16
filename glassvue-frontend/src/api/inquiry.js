import { apiGet, apiPost, apiPut, apiDelete } from './client';

// 상품 문의 목록. 응답: PageResponse<InquiryResponse>
// (비밀글은 서버가 작성자·관리자 외에는 content/answer를 마스킹해서 내려줌: masked=true)
export function fetchProductInquiries(productId, { page = 0, size = 5 } = {}) {
  return apiGet(`/api/products/${productId}/inquiries`, { page, size });
}

export function createInquiry(productId, payload) {
  return apiPost(`/api/products/${productId}/inquiries`, payload); // { title, content, secret }
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
