import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fetchUnreadCount, markNotificationRead, markAllNotificationsRead } from '../api/notification';
import { refreshSession } from '../api/client';
import { authState, setTokens, clearSession } from './auth';
import {
  notificationState,
  markRead,
  markAllRead,
  disconnectNotifications,
  connectNotifications,
} from './notifications';

vi.mock('../api/notification', () => ({
  fetchUnreadCount: vi.fn(),
  fetchNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}));

vi.mock('../api/client', () => ({ refreshSession: vi.fn() }));

beforeEach(() => {
  notificationState.items = [];
  notificationState.unread = 0;
  notificationState.loaded = false;
  vi.mocked(markNotificationRead).mockReset().mockResolvedValue(undefined);
  vi.mocked(markAllNotificationsRead).mockReset().mockResolvedValue(undefined);
  vi.mocked(fetchUnreadCount).mockReset().mockResolvedValue(0);
});

describe('markRead — 낙관적 읽음', () => {
  it('안읽은 알림을 읽으면 뱃지가 하나 준다', async () => {
    notificationState.items = [{ id: 'n1', read: false }, { id: 'n2', read: false }];
    notificationState.unread = 2;

    await markRead('n1');

    expect(notificationState.items.find((n) => n.id === 'n1').read).toBe(true);
    expect(notificationState.unread).toBe(1);
    expect(markNotificationRead).toHaveBeenCalledWith('n1');
  });

  it('이미 읽은 알림은 뱃지를 더 줄이지 않는다', async () => {
    notificationState.items = [{ id: 'n1', read: true }];
    notificationState.unread = 0;

    await markRead('n1');

    expect(notificationState.unread).toBe(0); // 음수로 안 내려간다
  });
});

describe('markAllRead', () => {
  it('전부 읽음 처리하고 뱃지를 0으로', async () => {
    notificationState.items = [{ id: 'n1', read: false }, { id: 'n2', read: false }];
    notificationState.unread = 2;

    await markAllRead();

    expect(notificationState.items.every((n) => n.read)).toBe(true);
    expect(notificationState.unread).toBe(0);
    expect(markAllNotificationsRead).toHaveBeenCalled();
  });
});

/**
 * 스트림 재연결 — **만료된 토큰으로 영원히 401 을 맞던 자리** (2026-08-05, 조건부 잔여 8번).
 *
 * 실측(2026-08-04): `/api/notifications/stream` 이 하루 **200 7건 · 401 108건**.
 * 백오프도 401→refresh 로직도 이미 있었는데, **스트림이 `request()` 를 안 거쳐**(생 fetch)
 * 그 갱신 경로를 통째로 비켜간 것이 원인이었다.
 */
describe('openStream — 401 재연결 정책', () => {
  const unauthorized = () => ({ ok: false, status: 401, body: null });
  // 붙자마자 끝나는 스트림(본문이 즉시 done) — 연결 성공 경로만 태우고 빠져나온다.
  const emptyStream = () => ({
    ok: true,
    status: 200,
    body: { getReader: () => ({ read: async () => ({ value: undefined, done: true }) }) },
  });

  beforeEach(() => {
    disconnectNotifications(); // 이전 테스트의 루프를 확실히 끊는다
    setTokens('AT', 'RT');
    vi.mocked(refreshSession).mockReset();
    globalThis.fetch = vi.fn();
  });

  afterEach(() => {
    disconnectNotifications(); // 백오프 타이머가 남아도 no-op 이 되게
    clearSession();
  });

  it('401 이면 **스스로 갱신하고 다시 붙는다**(다른 REST 호출을 기다리지 않는다)', async () => {
    globalThis.fetch.mockResolvedValueOnce(unauthorized()).mockResolvedValueOnce(emptyStream());
    vi.mocked(refreshSession).mockResolvedValue(true);

    connectNotifications();

    // ⚠ 갱신 직후 재연결도 백오프 경로(1초)를 탄다 — 즉시가 아니다(운영 코드 주석 참조).
    await vi.waitFor(() => expect(globalThis.fetch).toHaveBeenCalledTimes(2), { timeout: 3000 });
    expect(refreshSession).toHaveBeenCalledTimes(1);
  });

  it('갱신이 실패하면 멈추되 **세션은 건드리지 않는다**(배경 작업이 로그아웃을 정하지 않는다)', async () => {
    globalThis.fetch.mockResolvedValue(unauthorized());
    vi.mocked(refreshSession).mockResolvedValue(false);

    connectNotifications();

    await vi.waitFor(() => expect(refreshSession).toHaveBeenCalledTimes(1), { timeout: 3000 });
    // 더 두드리지 않는다
    await new Promise((r) => setTimeout(r, 1500));
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    // 그리고 토큰은 그대로다 — REST 의 request() 와 달리 clearSession 하지 않는다
    expect(authState.access).toBe('AT');
    expect(authState.refresh).toBe('RT');
  });

  it('🔴 갱신하고도 또 401 이면 **더 두드리지 않는다**(뜨거운 재시도 루프 금지)', async () => {
    globalThis.fetch.mockResolvedValue(unauthorized());
    vi.mocked(refreshSession).mockResolvedValue(true);

    connectNotifications();

    await vi.waitFor(() => expect(globalThis.fetch).toHaveBeenCalledTimes(2), { timeout: 3000 });
    await new Promise((r) => setTimeout(r, 1500)); // 다음 백오프(1초)가 지나도
    expect(globalThis.fetch).toHaveBeenCalledTimes(2); // 세 번째는 없다
    expect(refreshSession).toHaveBeenCalledTimes(1); // 갱신도 다시 부르지 않는다
  });
});

describe('disconnectNotifications', () => {
  it('상태를 비운다(다음 사용자에게 안 새게)', () => {
    notificationState.items = [{ id: 'n1', read: false }];
    notificationState.unread = 1;
    notificationState.loaded = true;

    disconnectNotifications();

    expect(notificationState.items).toEqual([]);
    expect(notificationState.unread).toBe(0);
    expect(notificationState.loaded).toBe(false);
  });
});
