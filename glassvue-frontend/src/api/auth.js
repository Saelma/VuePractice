import { apiGet, apiPost } from './client';
import { authState, setTokens, setUser, clearSession } from '../stores/auth';
import { clearWishlist } from '../stores/wishlist';
import { clearRestock } from '../stores/restock';

export function signup(payload) {
  return apiPost('/api/auth/signup', payload);
}

export async function login(payload) {
  const tokens = await apiPost('/api/auth/login', payload);
  setTokens(tokens.accessToken, tokens.refreshToken);
  const me = await apiGet('/api/auth/me');
  setUser(me);
  return me;
}

export async function logout() {
  try {
    await apiPost('/api/auth/logout');
  } catch (e) {
    /* 서버 로그아웃 실패해도 클라이언트 세션은 정리 */
  }
  clearSession();
  clearWishlist(); // 안 비우면 다음 사람이 남의 하트를 본다
  clearRestock(); // 재입고 신청 상태도 같은 이유로 비운다
}

// 앱 로드 시 저장된 토큰으로 내 정보 갱신(유효성 확인 겸)
export async function loadMe() {
  if (!authState.access) return null;
  try {
    const me = await apiGet('/api/auth/me');
    setUser(me);
    return me;
  } catch (e) {
    clearSession();
    clearWishlist();
    clearRestock();
    return null;
  }
}
