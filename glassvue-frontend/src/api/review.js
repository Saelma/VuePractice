import { apiGet, apiPost, apiPut, apiDelete } from './client';

// 상품 리뷰 목록 + 요약. 응답: { averageRating, reviewCount, page: PageResponse<ReviewResponse> }
//
// ⚠ **요약(averageRating·reviewCount)은 필터의 영향을 받지 않는다** — 그 상품 **전체** 기준이다.
//    사진 필터를 걸었다고 평균이 달라지면 상품 카드의 별점과 어긋나, 같은 상품인데 화면마다
//    다른 평점이 뜬다(서버가 그렇게 설계돼 있다, B-22).
export function fetchProductReviews(productId, { page = 0, size = 5, sort = null, photoOnly = false } = {}) {
  return apiGet(`/api/products/${productId}/reviews`, { page, size, sort, photoOnly });
}

/**
 * 리뷰 정렬 옵션 — 서버 화이트리스트(`createdAt`·`updatedAt`·`rating`)와 맞춰야 한다.
 * 여기 없는 값을 보내면 서버가 **400**으로 거부한다(상품 목록 SORT_OPTIONS 와 같은 방식).
 *
 * ⚠ 정렬 자체는 **B-22 이전부터 서버에 있었다** — 화면이 `?sort` 를 안 보내서 최신순만 쓰였을 뿐이다.
 */
export const REVIEW_SORT_OPTIONS = [
  { value: 'createdAt,desc', text: '최신순' },
  { value: 'rating,desc', text: '별점 높은순' },
  // 낮은순을 함께 연다 — 높은순만 있으면 "안 좋은 점"을 찾는 사람이 끝까지 넘겨야 한다.
  { value: 'rating,asc', text: '별점 낮은순' },
];

// payload: { rating, content, imageIds } — imageIds는 포토 리뷰(최대 5장), 없으면 빈 배열
export function createReview(productId, payload) {
  return apiPost(`/api/products/${productId}/reviews`, payload);
}

/** 리뷰 이미지 최대 장수 — 서버 @Size(max=5)와 맞춘 값. */
export const REVIEW_IMAGE_MAX = 5;

export function updateReview(id, payload) {
  return apiPut(`/api/reviews/${id}`, payload);
}

export function deleteReview(id) {
  return apiDelete(`/api/reviews/${id}`);
}

// ─────────────────────────────────────────────────────────────────────────────
// 관리자 리뷰 관리 (2026-08-04, 백로그 B-18)
//
// 이 셋이 생기기 전까지 **관리자 리뷰 API 는 0개**였다 — 작성자 본인만 지울 수 있어서
// 욕설·광고 리뷰가 올라오면 아무도 손댈 수 없었다.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 관리자 리뷰 목록. 상품을 **가로질러** 전체를 본다(고객 목록은 상품별이다).
 *
 * ⚠ `hidden` 은 **세 가지 상태**다 — `null`(전체) · `true`(숨긴 것만) · `false`(보이는 것만).
 *    `false` 와 `null` 은 다르다: 안 보내면 숨긴 것도 함께 온다.
 *    `apiGet` 은 `null` 인 파라미터를 빼므로 그대로 넘기면 된다.
 */
export function fetchAdminReviews({ hidden = null, page = 0, size = 20, sort = null } = {}) {
  return apiGet('/api/admin/reviews', { hidden, page, size, sort });
}

/** 리뷰 숨김(관리자). **삭제가 아니다** — 원문이 남아 되돌릴 수 있다. */
export function hideReview(id) {
  return apiPost(`/api/admin/reviews/${id}/hide`);
}

/** 숨김 해제(관리자). 목록·별점 집계에 다시 들어간다. */
export function unhideReview(id) {
  return apiPost(`/api/admin/reviews/${id}/unhide`);
}

/**
 * 숨김 상태 필터 선택지.
 *
 * ⚠ 「전체」의 값이 `null` 이어야 파라미터가 빠진다 — `'all'` 같은 문자열을 보내면
 * 서버가 Boolean 변환에 실패해 **400** 이 나고, 화면에는 그게 "리뷰가 없다" 로 보인다
 * (관리자 주문 목록의 `?status` 에서 배운 것과 같은 자리).
 */
export const REVIEW_HIDDEN_OPTIONS = [
  { value: null, text: '전체' },
  { value: false, text: '보이는 것만' },
  { value: true, text: '숨긴 것만' },
];
