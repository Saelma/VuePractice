import { apiGet, apiPost } from './client';

export function fetchCategories() {
  return apiGet('/api/categories');
}

export function createCategory(name) {
  return apiPost('/api/categories', { name });
}
