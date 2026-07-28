import { apiGet, apiPost } from './client';
import { authState, setTokens, setUser, clearSession } from '../stores/auth';
import { clearWishlist } from '../stores/wishlist';
import { clearRestock } from '../stores/restock';

export function signup(payload) {
  return apiPost('/api/auth/signup', payload);
}

// 비밀번호 재설정 요청 — 아이디로 재설정 토큰(링크) 발급.
// 응답의 token은 dev에서만 채워진다(운영은 null; 실제로는 메일/SMS로 링크가 나가야 함).
export function requestPasswordReset(loginId) {
  return apiPost('/api/auth/password-reset/request', { loginId });
}

// 토큰 + 새 비밀번호로 실제 변경. 성공 후에는 새 비밀번호로 다시 로그인해야 한다.
export function confirmPasswordReset(token, newPassword) {
  return apiPost('/api/auth/password-reset/confirm', { token, newPassword });
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
