import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fetchUnreadCount, markNotificationRead, markAllNotificationsRead } from '../api/notification';
import { notificationState, markRead, markAllRead, disconnectNotifications } from './notifications';

vi.mock('../api/notification', () => ({
  fetchUnreadCount: vi.fn(),
  fetchNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}));

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
