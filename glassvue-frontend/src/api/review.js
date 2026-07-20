import { apiGet, apiPost, apiPut, apiDelete } from './client';

// 상품 리뷰 목록 + 요약. 응답: { averageRating, reviewCount, page: PageResponse<ReviewResponse> }
export function fetchProductReviews(productId, { page = 0, size = 5 } = {}) {
  return apiGet(`/api/products/${productId}/reviews`, { page, size });
}

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
