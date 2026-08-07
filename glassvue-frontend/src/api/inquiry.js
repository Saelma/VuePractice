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

// ─────────────────────────────────────────────────────────────────────────────
// 일반 고객센터 문의 · 내 문의 (2026-08-07, 백로그 G-3 2·3단계)
//
// 1단계가 관리자 **목록**을 열어 *"답할 경로가 없다"* 를 풀었지만, 작성 경로는 여전히
// `POST /products/{id}/inquiries` **하나**뿐이었다 — 즉 *"배송이 안 와요"* 를 물으려면
// **아무 상품이나 골라야** 했다. 여기가 그 반대쪽을 연다.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 일반 고객센터 문의 작성. **상품이 없다.**
 *
 * ⚠ `type` 에 `'PRODUCT'` 를 보내면 **400**(`INQUIRY-400T`)이다. 서버가 조용히 다른 값으로
 *    바꾸지 않는다 — 그러면 사용자가 고른 유형이 사라진 채 성공 응답이 나간다.
 *    상품 문의는 `createInquiry`(상품 경로)로 만들고, 그때 유형은 **경로가 정한다.**
 */
export function createGeneralInquiry(payload) {
  return apiPost('/api/inquiries', payload); // { type, title, content, secret, imageIds }
}

/** 내 문의 목록. 상품 문의·일반 문의가 **한 목록에 섞여** 온다(줄마다 `type` 이 실린다). */
export function fetchMyInquiries({ page = 0, size = 10 } = {}) {
  return apiGet('/api/inquiries/me', { page, size });
}

/**
 * 유형 표시 문구. 서버 enum 은 6종인데 **화면에서 고르는 것은 4종**이다:
 * - `PRODUCT` 는 고르는 값이 아니라 **경로가 정하는** 값이라 선택지에 없다.
 * - `PAYMENT`·`ACCOUNT` 는 V42 에서 값만 미리 열어 뒀다(Oracle 은 나중에 enum 을 늘리면
 *   CHECK 제약을 못 고쳐 수동 ALTER 가 필요하다). 필요해지면 아래 선택지에만 더하면 된다.
 *
 * ⚠ 그래서 **표시 문구는 6종 전부** 가지고 있어야 한다 — 목록에는 PRODUCT 줄이 섞여 오고,
 *    나중에 PAYMENT 로 들어온 문의가 «PAYMENT» 라는 날문자로 보이면 안 된다.
 */
export const INQUIRY_TYPE_TEXT = {
  PRODUCT: '상품 문의',
  DELIVERY: '배송',
  REFUND: '환불·취소',
  PAYMENT: '결제',
  ACCOUNT: '회원·계정',
  ETC: '기타',
};
export function inquiryTypeText(type) {
  return INQUIRY_TYPE_TEXT[type] || type;
}

/** 고객센터 문의 폼의 유형 선택지 — 위 주석대로 PRODUCT 는 없다. */
export const GENERAL_INQUIRY_TYPE_OPTIONS = [
  { value: 'DELIVERY', text: INQUIRY_TYPE_TEXT.DELIVERY },
  { value: 'REFUND', text: INQUIRY_TYPE_TEXT.REFUND },
  { value: 'ETC', text: INQUIRY_TYPE_TEXT.ETC },
];
