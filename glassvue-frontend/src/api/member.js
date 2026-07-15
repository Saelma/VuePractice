import { apiPatch, apiDelete } from './client';
import { setUser, clearSession } from '../stores/auth';

export async function changeNickname(nickname) {
  const me = await apiPatch('/api/members/me/nickname', { nickname });
  setUser(me); // 헤더 닉네임 즉시 갱신 (토큰 claim은 다음 로그인 때 갱신)
  return me;
}

export function changePassword(currentPassword, newPassword) {
  return apiPatch('/api/members/me/password', { currentPassword, newPassword });
}

export async function withdraw() {
  await apiDelete('/api/members/me');
  clearSession();
}
