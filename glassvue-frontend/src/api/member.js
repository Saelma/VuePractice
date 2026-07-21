import { apiPatch, apiDelete } from './client';
import { setUser, clearSession } from '../stores/auth';

export async function changeNickname(nickname) {
  const me = await apiPatch('/api/members/me/nickname', { nickname });
  setUser(me); // 헤더 닉네임 즉시 갱신 (토큰 claim은 다음 로그인 때 갱신)
  return me;
}

/** 기본 배송지 저장. 응답이 갱신된 회원이라 스토어도 같이 갱신해 주문서 자동 채움에 바로 반영된다. */
export async function updateShippingAddress(address) {
  const me = await apiPatch('/api/members/me/shipping-address', address);
  setUser(me);
  return me;
}

export function changePassword(currentPassword, newPassword) {
  return apiPatch('/api/members/me/password', { currentPassword, newPassword });
}

export async function withdraw() {
  await apiDelete('/api/members/me');
  clearSession();
}
