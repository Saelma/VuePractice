import { apiGet, apiPost, apiPut, apiDelete } from './client';

// 상품 리뷰 목록 + 요약. 응답: { averageRating, reviewCount, page: PageResponse<ReviewResponse> }
export function fetchProductReviews(productId, { page = 0, size = 5 } = {}) {
  return apiGet(`/api/products/${productId}/reviews`, { page, size });
}

export function createReview(productId, payload) {
  return apiPost(`/api/products/${productId}/reviews`, payload); // { rating, content }
}

export function updateReview(id, payload) {
  return apiPut(`/api/reviews/${id}`, payload);
}

export function deleteReview(id) {
  return apiDelete(`/api/reviews/${id}`);
}
