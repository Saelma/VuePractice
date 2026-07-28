import { reactive, computed } from 'vue';

// 로그인 상태(토큰 + 사용자). localStorage와 동기화해 새로고침에도 유지.
const state = reactive({
  access: localStorage.getItem('accessToken'),
  refresh: localStorage.getItem('refreshToken'),
  user: JSON.parse(localStorage.getItem('user') || 'null'),
});

export const authState = state;
export const isLoggedIn = computed(() => !!state.access);

// 관리자 판별은 한 곳에서. SUPER_ADMIN(최상위 관리자)은 ADMIN 을 포함하므로 관리 UI·가드를 그대로 통과한다.
// (백엔드 Role.authorities 와 같은 규칙 — 흩어진 role==='ADMIN' 비교가 super 를 놓치지 않게 여기로 모은다.)
export function isAdminRole(role) {
  return role === 'ADMIN' || role === 'SUPER_ADMIN';
}
export const isAdmin = computed(() => isAdminRole(state.user?.role));

export function setTokens(access, refresh) {
  state.access = access;
  state.refresh = refresh;
  localStorage.setItem('accessToken', access);
  localStorage.setItem('refreshToken', refresh);
}

export function setUser(user) {
  state.user = user;
  localStorage.setItem('user', JSON.stringify(user));
}

export function clearSession() {
  state.access = null;
  state.refresh = null;
  state.user = null;
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('user');
}
