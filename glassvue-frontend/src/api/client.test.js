import { describe, it, expect, beforeEach, vi } from 'vitest';
import { apiGet } from './client';
import { authState, setTokens, setUser, clearSession, isAdmin } from '../stores/auth';

// fetch 응답 목킹 헬퍼
const res = (status, body) => ({
  ok: status >= 200 && status < 300,
  status,
  json: () => Promise.resolve(body),
});

describe('api/client — ApiResponse 언랩 + 401 자동 재발급', () => {
  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  it('성공 시 data만 벗겨서 반환', async () => {
    global.fetch = vi.fn().mockResolvedValue(res(200, { success: true, data: { id: 'x' } }));
    await expect(apiGet('/api/x')).resolves.toEqual({ id: 'x' });
  });

  it('에러 시 message·code·status 담아 throw', async () => {
    global.fetch = vi.fn().mockResolvedValue(
      res(400, { success: false, error: { code: 'E-400', message: '나쁜 요청' } }));
    await expect(apiGet('/api/x')).rejects.toMatchObject({ message: '나쁜 요청', code: 'E-400', status: 400 });
  });

  it('로그인 상태면 Authorization 헤더 자동 첨부', async () => {
    setTokens('AT', 'RT');
    const fetchMock = vi.fn().mockResolvedValue(res(200, { success: true, data: 1 }));
    global.fetch = fetchMock;
    await apiGet('/api/x');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer AT');
  });

  it('401 → refresh 1회 → 원요청 재시도 성공, 토큰 회전', async () => {
    setTokens('OLD', 'REFRESH1');
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(res(401, { success: false, error: { code: 'AUTH', message: 'expired' } })) // 원요청
      .mockResolvedValueOnce(res(200, { success: true, data: { accessToken: 'NEW', refreshToken: 'REFRESH2' } })) // refresh
      .mockResolvedValueOnce(res(200, { success: true, data: { id: 'm1', role: 'USER' } })) // /auth/me 동기화
      .mockResolvedValueOnce(res(200, { success: true, data: 'ok' })); // 재시도
    global.fetch = fetchMock;

    await expect(apiGet('/api/x')).resolves.toBe('ok');
    expect(authState.access).toBe('NEW');
    expect(fetchMock).toHaveBeenCalledTimes(4); // 원요청 + refresh + /auth/me + 재시도
  });

  it('재발급 후 역할이 바뀌었으면 화면 상태(user.role)도 따라 바뀐다', async () => {
    // 강등 시나리오: 저장된 user 는 ADMIN 인데 재발급된 토큰의 실제 역할은 USER 다.
    // 갱신하지 않으면 관리 메뉴가 계속 보이고 가드도 통과한다(그리고 API 에서 403).
    setTokens('OLD', 'REFRESH1');
    setUser({ id: 'm1', loginId: 'admin1', role: 'ADMIN' });
    global.fetch = vi.fn()
      .mockResolvedValueOnce(res(401, { success: false, error: { code: 'AUTH', message: 'revoked' } }))
      .mockResolvedValueOnce(res(200, { success: true, data: { accessToken: 'NEW', refreshToken: 'REFRESH2' } }))
      .mockResolvedValueOnce(res(200, { success: true, data: { id: 'm1', loginId: 'admin1', role: 'USER' } }))
      .mockResolvedValueOnce(res(200, { success: true, data: 'ok' }));

    await expect(apiGet('/api/x')).resolves.toBe('ok');
    expect(authState.user.role).toBe('USER');
    expect(isAdmin.value).toBe(false);
  });

  it('/auth/me 갱신이 실패해도 재발급 자체는 성공으로 둔다', async () => {
    setTokens('OLD', 'REFRESH1');
    setUser({ id: 'm1', role: 'USER' });
    global.fetch = vi.fn()
      .mockResolvedValueOnce(res(401, { success: false, error: { code: 'AUTH', message: 'expired' } }))
      .mockResolvedValueOnce(res(200, { success: true, data: { accessToken: 'NEW', refreshToken: 'REFRESH2' } }))
      .mockRejectedValueOnce(new Error('네트워크 오류')) // /auth/me 실패
      .mockResolvedValueOnce(res(200, { success: true, data: 'ok' }));

    await expect(apiGet('/api/x')).resolves.toBe('ok');
    expect(authState.access).toBe('NEW');
  });

  it('401 → refresh 실패 → 세션 정리 후 throw', async () => {
    setTokens('OLD', 'REFRESH1');
    global.fetch = vi.fn()
      .mockResolvedValueOnce(res(401, { success: false, error: { code: 'AUTH', message: 'expired' } }))
      .mockResolvedValueOnce(res(401, { success: false })); // refresh 실패
    await expect(apiGet('/api/x')).rejects.toBeTruthy();
    expect(authState.access).toBeNull(); // clearSession 됨
  });
});
