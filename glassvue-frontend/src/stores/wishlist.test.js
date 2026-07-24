import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fetchWishlistProductIds, addWishlist, removeWishlist } from '../api/wishlist';
import { wishlistState, isWishlisted, loadWishlistIds, toggleWishlist, clearWishlist } from './wishlist';

vi.mock('../api/wishlist', () => ({
  fetchWishlistProductIds: vi.fn(),
  addWishlist: vi.fn(),
  removeWishlist: vi.fn(),
}));

beforeEach(() => {
  clearWishlist();
  vi.mocked(fetchWishlistProductIds).mockReset();
  vi.mocked(addWishlist).mockReset().mockResolvedValue(undefined);
  vi.mocked(removeWishlist).mockReset().mockResolvedValue(undefined);
});

describe('loadWishlistIds', () => {
  it('받아온 id를 집합에 담는다', async () => {
    vi.mocked(fetchWishlistProductIds).mockResolvedValueOnce(['p1', 'p2']);
    await loadWishlistIds();
    expect(isWishlisted('p1')).toBe(true);
    expect(isWishlisted('p3')).toBe(false);
  });

  it('이미 받았으면 다시 부르지 않는다 — 화면마다 mount될 때 중복 요청을 막는다', async () => {
    vi.mocked(fetchWishlistProductIds).mockResolvedValue(['p1']);
    await loadWishlistIds();
    await loadWishlistIds();
    expect(fetchWishlistProductIds).toHaveBeenCalledTimes(1);
  });

  it('force면 다시 받는다', async () => {
    vi.mocked(fetchWishlistProductIds).mockResolvedValue(['p1']);
    await loadWishlistIds();
    await loadWishlistIds(true);
    expect(fetchWishlistProductIds).toHaveBeenCalledTimes(2);
  });

  it('실패해도 던지지 않는다 — 하트가 빈 채로 보일 뿐 상품 열람은 되어야 한다', async () => {
    vi.mocked(fetchWishlistProductIds).mockRejectedValueOnce(new Error('401'));
    await expect(loadWishlistIds()).resolves.toBeUndefined();
    expect(wishlistState.ids.size).toBe(0);
  });
});

describe('toggleWishlist', () => {
  it('찜 안 한 상품 → 추가되고 true를 돌려준다', async () => {
    await expect(toggleWishlist('p1')).resolves.toBe(true);
    expect(isWishlisted('p1')).toBe(true);
    expect(addWishlist).toHaveBeenCalledWith('p1');
  });

  it('찜한 상품 → 해제되고 false를 돌려준다', async () => {
    await toggleWishlist('p1');
    await expect(toggleWishlist('p1')).resolves.toBe(false);
    expect(isWishlisted('p1')).toBe(false);
    expect(removeWishlist).toHaveBeenCalledWith('p1');
  });

  it('화면을 먼저 바꾼다 — 서버 응답을 기다리지 않고 하트가 즉시 채워진다', () => {
    let resolveAdd;
    vi.mocked(addWishlist).mockReturnValueOnce(new Promise((r) => { resolveAdd = r; }));
    const pending = toggleWishlist('p1');
    expect(isWishlisted('p1')).toBe(true); // 아직 서버 응답 전인데 이미 켜져 있다
    resolveAdd();
    return pending;
  });

  it('서버가 실패하면 되돌린다 — 화면과 서버가 어긋난 채 남으면 안 된다', async () => {
    vi.mocked(addWishlist).mockRejectedValueOnce(new Error('네트워크 오류'));
    await expect(toggleWishlist('p1')).rejects.toThrow('네트워크 오류');
    expect(isWishlisted('p1')).toBe(false);
  });

  it('해제 실패도 되돌린다', async () => {
    await toggleWishlist('p1');
    vi.mocked(removeWishlist).mockRejectedValueOnce(new Error('실패'));
    await expect(toggleWishlist('p1')).rejects.toThrow('실패');
    expect(isWishlisted('p1')).toBe(true);
  });
});

describe('clearWishlist', () => {
  it('로그아웃 시 비운다 — 안 비우면 다음 사람이 남의 하트를 본다', async () => {
    await toggleWishlist('p1');
    clearWishlist();
    expect(isWishlisted('p1')).toBe(false);
    expect(wishlistState.loaded).toBe(false);
  });
});
