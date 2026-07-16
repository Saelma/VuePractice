import { describe, it, expect, beforeEach } from 'vitest';
import { authState, isLoggedIn, setTokens, setUser, clearSession } from './auth';

describe('auth 스토어', () => {
  beforeEach(() => clearSession());

  it('초기: 비로그인', () => {
    expect(isLoggedIn.value).toBe(false);
  });

  it('setTokens: 상태 + localStorage 저장, isLoggedIn true', () => {
    setTokens('AT', 'RT');
    expect(authState.access).toBe('AT');
    expect(authState.refresh).toBe('RT');
    expect(localStorage.getItem('accessToken')).toBe('AT');
    expect(localStorage.getItem('refreshToken')).toBe('RT');
    expect(isLoggedIn.value).toBe(true);
  });

  it('setUser: 사용자 저장(직렬화)', () => {
    setUser({ id: '1', role: 'ADMIN', nickname: '관리자' });
    expect(authState.user.role).toBe('ADMIN');
    expect(JSON.parse(localStorage.getItem('user')).nickname).toBe('관리자');
  });

  it('clearSession: 토큰·유저·localStorage 전부 정리', () => {
    setTokens('AT', 'RT');
    setUser({ id: '1' });
    clearSession();
    expect(authState.access).toBeNull();
    expect(authState.user).toBeNull();
    expect(isLoggedIn.value).toBe(false);
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });
});
