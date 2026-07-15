import { apiGet, apiPost, apiPut, apiDelete } from './client';

// 목록 검색. 빈 파라미터는 client에서 자동 제외(동적 검색).
// 반환: PageResponse { content, page, size, totalElements, totalPages, last }
export function fetchNotices({ title, author, fromDate, toDate, page = 0, size = 10 } = {}) {
  return apiGet('/api/notices', { title, author, fromDate, toDate, page, size });
}

// 단건 조회. 반환: NoticeResponse
export function getNotice(id) {
  return apiGet(`/api/notices/${id}`);
}

// 등록. 반환: 생성된 id(UUID)
export function createNotice(payload) {
  return apiPost('/api/notices', payload);
}

// 수정 (title, content, pinned)
export function updateNotice(id, payload) {
  return apiPut(`/api/notices/${id}`, payload);
}

// 삭제
export function deleteNotice(id) {
  return apiDelete(`/api/notices/${id}`);
}

// 조회수 증가
export function increaseView(id) {
  return apiPost(`/api/notices/${id}/views`);
}
