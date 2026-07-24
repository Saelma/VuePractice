import { describe, it, expect, beforeEach } from 'vitest';
import { recentlyViewed, pushRecentlyViewed } from './recentlyViewed';
import { setUser, clearSession } from './auth';

// 상품 스냅샷 헬퍼 — 최소 필드만.
const prod = (id) => ({ id, name: `상품${id}`, price: 1000, images: [{ thumbUrl: `/t/${id}.webp` }] });

beforeEach(() => {
  localStorage.clear();
  clearSession(); // guest 상태로
  recentlyViewed.value = []; // 싱글턴이라 테스트 간 초기화
});

describe('recentlyViewed — 계정 스코프', () => {
  it('현재 계정 키에 저장한다 (guest/계정 분리)', () => {
    setUser({ id: 'A' });
    pushRecentlyViewed(prod('p1'));
    expect(JSON.parse(localStorage.getItem('glassvue.recentlyViewed.A'))[0].id).toBe('p1');
    expect(localStorage.getItem('glassvue.recentlyViewed.guest')).toBeNull();
  });

  it('다른 계정으로 바꾸면 그 계정 목록으로 갈아끼운다 (누수 없음)', () => {
    setUser({ id: 'A' });
    pushRecentlyViewed(prod('p1'));
    expect(recentlyViewed.value.map((p) => p.id)).toEqual(['p1']);

    setUser({ id: 'B' }); // 다른 계정 — A의 목록이 보이면 안 된다
    expect(recentlyViewed.value).toEqual([]);
    pushRecentlyViewed(prod('p2'));

    setUser({ id: 'A' }); // 돌아오면 A의 목록이 그대로
    expect(recentlyViewed.value.map((p) => p.id)).toEqual(['p1']);
  });

  it('로그아웃하면 guest 목록으로 (계정 목록 안 보임)', () => {
    setUser({ id: 'A' });
    pushRecentlyViewed(prod('p1'));
    clearSession();
    expect(recentlyViewed.value).toEqual([]); // guest 는 비어 있음
  });

  it('중복은 제거하고 최신 위치로 끌어올린다', () => {
    setUser({ id: 'A' });
    pushRecentlyViewed(prod('p1'));
    pushRecentlyViewed(prod('p2'));
    pushRecentlyViewed(prod('p1')); // 다시 p1
    expect(recentlyViewed.value.map((p) => p.id)).toEqual(['p1', 'p2']);
  });

  it('최대 10개까지만, 초과분은 오래된 것부터 밀려난다(FIFO)', () => {
    setUser({ id: 'A' });
    for (let i = 1; i <= 12; i += 1) pushRecentlyViewed(prod(`p${i}`));
    expect(recentlyViewed.value).toHaveLength(10);
    expect(recentlyViewed.value[0].id).toBe('p12'); // 최신이 맨 앞
    expect(recentlyViewed.value.map((p) => p.id)).not.toContain('p1'); // 가장 오래된 둘은 밀려남
    expect(recentlyViewed.value.map((p) => p.id)).not.toContain('p2');
  });
});
