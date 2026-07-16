import { describe, it, expect, beforeEach, vi } from 'vitest';
import { apiGet } from './client';
import { authState, setTokens, clearSession } from '../stores/auth';

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
      .mockResolvedValueOnce(res(200, { success: true, data: 'ok' })); // 재시도
    global.fetch = fetchMock;

    await expect(apiGet('/api/x')).resolves.toBe('ok');
    expect(authState.access).toBe('NEW');
    expect(fetchMock).toHaveBeenCalledTimes(3);
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
