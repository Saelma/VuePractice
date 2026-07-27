import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fetchRestockProductIds, subscribeRestock, unsubscribeRestock } from '../api/restock';
import { restockState, isRestockSubscribed, loadRestockIds, toggleRestock, clearRestock } from './restock';

vi.mock('../api/restock', () => ({
  fetchRestockProductIds: vi.fn(),
  subscribeRestock: vi.fn(),
  unsubscribeRestock: vi.fn(),
}));

beforeEach(() => {
  clearRestock();
  vi.mocked(fetchRestockProductIds).mockReset();
  vi.mocked(subscribeRestock).mockReset().mockResolvedValue(undefined);
  vi.mocked(unsubscribeRestock).mockReset().mockResolvedValue(undefined);
});

describe('loadRestockIds', () => {
  it('받아온 id를 집합에 담는다', async () => {
    vi.mocked(fetchRestockProductIds).mockResolvedValueOnce(['p1', 'p2']);
    await loadRestockIds();
    expect(isRestockSubscribed('p1')).toBe(true);
    expect(isRestockSubscribed('p3')).toBe(false);
  });

  it('이미 받았으면 다시 부르지 않는다', async () => {
    vi.mocked(fetchRestockProductIds).mockResolvedValue(['p1']);
    await loadRestockIds();
    await loadRestockIds();
    expect(fetchRestockProductIds).toHaveBeenCalledTimes(1);
  });

  it('force면 다시 받는다', async () => {
    vi.mocked(fetchRestockProductIds).mockResolvedValue(['p1']);
    await loadRestockIds();
    await loadRestockIds(true);
    expect(fetchRestockProductIds).toHaveBeenCalledTimes(2);
  });

  it('실패해도 던지지 않는다 — 버튼이 "신청" 상태로 보일 뿐 상품 열람은 되어야 한다', async () => {
    vi.mocked(fetchRestockProductIds).mockRejectedValueOnce(new Error('401'));
    await expect(loadRestockIds()).resolves.toBeUndefined();
    expect(restockState.ids.size).toBe(0);
  });
});

describe('toggleRestock', () => {
  it('신청 안 한 상품 → 신청되고 true를 돌려준다', async () => {
    await expect(toggleRestock('p1')).resolves.toBe(true);
    expect(isRestockSubscribed('p1')).toBe(true);
    expect(subscribeRestock).toHaveBeenCalledWith('p1');
  });

  it('신청한 상품 → 취소되고 false를 돌려준다', async () => {
    await toggleRestock('p1');
    await expect(toggleRestock('p1')).resolves.toBe(false);
    expect(isRestockSubscribed('p1')).toBe(false);
    expect(unsubscribeRestock).toHaveBeenCalledWith('p1');
  });

  it('화면을 먼저 바꾼다 — 서버 응답 전에 이미 신청 상태가 된다', () => {
    let resolveSub;
    vi.mocked(subscribeRestock).mockReturnValueOnce(new Promise((r) => { resolveSub = r; }));
    const pending = toggleRestock('p1');
    expect(isRestockSubscribed('p1')).toBe(true);
    resolveSub();
    return pending;
  });

  it('서버가 실패하면 되돌린다', async () => {
    vi.mocked(subscribeRestock).mockRejectedValueOnce(new Error('네트워크 오류'));
    await expect(toggleRestock('p1')).rejects.toThrow('네트워크 오류');
    expect(isRestockSubscribed('p1')).toBe(false);
  });

  it('취소 실패도 되돌린다', async () => {
    await toggleRestock('p1');
    vi.mocked(unsubscribeRestock).mockRejectedValueOnce(new Error('실패'));
    await expect(toggleRestock('p1')).rejects.toThrow('실패');
    expect(isRestockSubscribed('p1')).toBe(true);
  });
});

describe('clearRestock', () => {
  it('로그아웃 시 비운다 — 안 비우면 다음 사람이 남의 신청 상태를 본다', async () => {
    await toggleRestock('p1');
    clearRestock();
    expect(isRestockSubscribed('p1')).toBe(false);
    expect(restockState.loaded).toBe(false);
  });
});
