import { apiGet } from './client';

// 공지 목록 검색.
// 값이 비어있는 파라미터(undefined/null/'')는 client에서 자동으로 제외된다(동적 검색).
// 반환: PageResponse { content, page, size, totalElements, totalPages, last }
export function fetchNotices({
  title,
  author,
  fromDate,
  toDate,
  page = 0,
  size = 10,
} = {}) {
  return apiGet('/api/notices', { title, author, fromDate, toDate, page, size });
}
