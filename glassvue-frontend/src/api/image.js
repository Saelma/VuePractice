import { apiUpload } from './client';

// 이미지 파일 업로드 → { id, url } 반환
export function uploadImage(file) {
  const formData = new FormData();
  formData.append('file', file);
  return apiUpload('/api/images', formData);
}
