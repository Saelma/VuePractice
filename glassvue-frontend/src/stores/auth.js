import { reactive, computed } from 'vue';

// 로그인 상태(토큰 + 사용자). localStorage와 동기화해 새로고침에도 유지.
const state = reactive({
  access: localStorage.getItem('accessToken'),
  refresh: localStorage.getItem('refreshToken'),
  user: JSON.parse(localStorage.getItem('user') || 'null'),
});

export const authState = state;
export const isLoggedIn = computed(() => !!state.access);

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
