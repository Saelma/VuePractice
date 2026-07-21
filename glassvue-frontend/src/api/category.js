import { apiGet, apiPost, apiDelete } from './client';

export function fetchCategories() {
  return apiGet('/api/categories');
}

export function createCategory(name) {
  return apiPost('/api/categories', { name });
}

// 소속 상품이 있으면 서버가 CATEGORY-409U(CATEGORY_IN_USE)로 막는다.
export function deleteCategory(id) {
  return apiDelete(`/api/categories/${id}`);
}
