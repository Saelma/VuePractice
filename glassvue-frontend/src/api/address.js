import { apiGet, apiPost, apiPut, apiPatch, apiDelete } from './client';

/**
 * 배송지 주소록 (2026-07-24, 백로그 B-5).
 *
 * V11~V17 동안은 배송지가 회원당 하나였고 `member.js`의 `updateShippingAddress`가 그걸 저장했다.
 * 그 경로는 **그대로 살아 있다** — 주문서의 "이 주소를 기본 배송지로 저장" 체크가 계속 쓴다.
 * 서버가 그 요청을 주소록의 기본 항목 저장으로 바꿔 처리하므로 주소가 두 벌로 갈라지지 않는다.
 *
 * 전부 본인 것만 다룬다(경로에 memberId가 없다 — 서버가 토큰에서 읽는다).
 */

/** 내 주소록. 기본 배송지가 맨 위. */
export function fetchAddresses() {
  return apiGet('/api/members/me/addresses');
}

/** 추가. 첫 주소는 setDefault와 무관하게 기본 배송지가 된다(서버 규칙). 최대 10개. */
export function addAddress(payload) {
  return apiPost('/api/members/me/addresses', payload);
}

export function updateAddress(id, payload) {
  return apiPut(`/api/members/me/addresses/${id}`, payload);
}

/** 기본 배송지 지정. 기존 기본은 서버가 해제한다(회원당 하나). */
export function setDefaultAddress(id) {
  return apiPatch(`/api/members/me/addresses/${id}/default`);
}

/** 삭제. 기본 배송지를 지우면 남은 것 중 가장 먼저 등록한 주소가 기본이 된다(서버 규칙). */
export function deleteAddress(id) {
  return apiDelete(`/api/members/me/addresses/${id}`);
}

/** 주소록 항목 → 배송지 폼 모양. 주문서에서 선택한 주소를 폼에 부을 때 쓴다. */
export function addressToForm(a) {
  return {
    recipient: a?.recipient || '',
    phone: a?.phone || '',
    zipcode: a?.zipcode || '',
    address1: a?.address1 || '',
    address2: a?.address2 || '',
  };
}

/** 목록에서 한 줄로 보여줄 요약. 별칭은 따로 보여주므로 여기엔 넣지 않는다. */
export function addressSummary(a) {
  if (!a) return '';
  return [`(${a.zipcode})`, a.address1, a.address2].filter(Boolean).join(' ');
}
