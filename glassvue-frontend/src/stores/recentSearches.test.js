import { describe, it, expect, beforeEach } from 'vitest';
import {
  recentSearches,
  pushRecentSearch,
  removeRecentSearch,
  clearRecentSearches,
} from './recentSearches';
import { setUser, clearSession } from './auth';

// 최근 검색어 (2026-09-01, BACKLOG G-7).
//
// ⚠ 계정 스코프 절은 recentlyViewed.test.js 와 **같은 것을 묻는다** — 구조를 그대로 가져왔으니
//    지키는 것도 그대로 지켜야 한다. 🔴 **검색어는 본 상품보다 사적이다**(«무엇을 찾고 있었나»).
//    그래서 계정 누수 절을 줄이지 않았다.

beforeEach(() => {
  localStorage.clear();
  clearSession(); // guest 상태로
  recentSearches.value = []; // 싱글턴이라 테스트 간 초기화
});

describe('recentSearches — 계정 스코프', () => {
  it('현재 계정 키에 저장한다 (guest/계정 분리)', () => {
    setUser({ id: 'A' });
    pushRecentSearch('지바');
    expect(JSON.parse(localStorage.getItem('glassvue.recentSearches.A'))).toEqual(['지바']);
    expect(localStorage.getItem('glassvue.recentSearches.guest')).toBeNull();
  });

  it('🔴 다른 계정으로 바꾸면 내 검색어가 안 보인다 (계정 누수)', () => {
    setUser({ id: 'A' });
    pushRecentSearch('지바');
    expect(recentSearches.value).toEqual(['지바']);

    setUser({ id: 'B' });
    expect(recentSearches.value).toEqual([]);
    pushRecentSearch('반팔티');

    setUser({ id: 'A' }); // 돌아오면 A 의 것이 그대로
    expect(recentSearches.value).toEqual(['지바']);
  });

  it('로그아웃하면 guest 목록으로 (계정 목록 안 보임)', () => {
    setUser({ id: 'A' });
    pushRecentSearch('지바');
    clearSession();
    expect(recentSearches.value).toEqual([]);
  });

  it('비회원도 담긴다 — 로그인 없이 검색해도 남는다', () => {
    pushRecentSearch('몽쉘');
    expect(recentSearches.value).toEqual(['몽쉘']);
    expect(JSON.parse(localStorage.getItem('glassvue.recentSearches.guest'))).toEqual(['몽쉘']);
  });
});

describe('recentSearches — 목록 규칙', () => {
  it('최신이 맨 앞', () => {
    pushRecentSearch('지바');
    pushRecentSearch('몽쉘');
    expect(recentSearches.value).toEqual(['몽쉘', '지바']);
  });

  it('같은 말은 하나로 — 다시 치면 맨 앞으로 올라온다', () => {
    pushRecentSearch('지바');
    pushRecentSearch('몽쉘');
    pushRecentSearch('지바');
    expect(recentSearches.value).toEqual(['지바', '몽쉘']);
  });

  it('⚠ 대소문자는 무시하고 하나로 보되, 남기는 것은 **마지막에 친 표기**다', () => {
    pushRecentSearch('Zibar');
    pushRecentSearch('zibar');
    expect(recentSearches.value).toEqual(['zibar']);
  });

  it('앞뒤 공백은 떼고 담는다 — 「 지바 」와 「지바」는 같은 말이다', () => {
    pushRecentSearch('  지바  ');
    pushRecentSearch('지바');
    expect(recentSearches.value).toEqual(['지바']);
  });

  it('빈 말·공백만 있는 말은 안 담는다 (빈 검색은 「전체 목록」이다)', () => {
    pushRecentSearch('');
    pushRecentSearch('   ');
    pushRecentSearch(null);
    pushRecentSearch(undefined);
    expect(recentSearches.value).toEqual([]);
  });

  it('최대 8개까지만, 초과분은 오래된 것부터 밀려난다(FIFO)', () => {
    for (let i = 1; i <= 10; i += 1) pushRecentSearch(`말${i}`);
    expect(recentSearches.value).toHaveLength(8);
    expect(recentSearches.value[0]).toBe('말10');
    expect(recentSearches.value).not.toContain('말1');
    expect(recentSearches.value).not.toContain('말2');
  });
});

describe('recentSearches — 지우기', () => {
  it('한 줄만 지운다 — 나머지는 남는다', () => {
    pushRecentSearch('지바');
    pushRecentSearch('몽쉘');
    removeRecentSearch('지바');
    expect(recentSearches.value).toEqual(['몽쉘']);
  });

  it('전체 지우기 — 저장소에서도 비워진다', () => {
    pushRecentSearch('지바');
    clearRecentSearches();
    expect(recentSearches.value).toEqual([]);
    expect(JSON.parse(localStorage.getItem('glassvue.recentSearches.guest'))).toEqual([]);
  });
});

describe('recentSearches — 저장소가 망가져 있어도 죽지 않는다', () => {
  it('JSON 이 깨졌으면 빈 목록으로 시작한다', () => {
    localStorage.setItem('glassvue.recentSearches.C', '{깨진 값');
    setUser({ id: 'C' });
    expect(recentSearches.value).toEqual([]);
  });

  it('⚠ 문자열이 아닌 값이 섞여 있으면 걸러 낸다 (손으로 고쳐진 저장소)', () => {
    localStorage.setItem('glassvue.recentSearches.D', JSON.stringify(['지바', 42, null, { a: 1 }, '']));
    setUser({ id: 'D' });
    expect(recentSearches.value).toEqual(['지바']);
  });
});
