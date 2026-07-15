import { apiGet } from './client';

// 공지 목록 검색. 반환: PageResponse { content, page, size, totalElements, totalPages, last }
export function fetchNotices({ title, author, page = 0, size = 10 } = {}) {
  return apiGet('/api/notices', { title, author, page, size });
}
