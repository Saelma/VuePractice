import { describe, it, expect, beforeEach } from 'vitest';
import { adoptGuestHistory } from './guestHistory';
import { recentlyViewed, pushRecentlyViewed } from './recentlyViewed';
import { recentSearches, pushRecentSearch } from './recentSearches';
import { setUser, clearSession } from './auth';

// 비회원 기록을 «가입할 때» 계정으로 옮긴다 (2026-09-01, BACKLOG J-5).
//
// 🔴 **이 파일이 지키는 것은 «옮긴다» 가 아니라 «언제·무엇을·원본을 어떻게 하나» 다.**
//    결정 셋이 있었고(사용자 확정), 셋 다 되돌리기 어려운 쪽이 있어서 여기서 못 박는다:
//      ① **가입 때만** — 로그인에서 하면 공용 PC 에서 앞사람 기록이 남의 계정으로 들어간다
//      ② **옮기고 guest 를 비운다** — 복사로 두면 다음 사람이 앞사람 기록을 본다(지금이 그 상태다)
//      ③ **둘 다 옮긴다** — 규칙을 둘로 가르면 «검색어만 사라진» 이유를 화면에서 설명할 수 없다
//
// ⚠ «로그인에서는 안 옮긴다» 는 **여기서 못 본다** — 그건 «안 부른다» 라서 부르는 쪽(SignupView)의
//    일이다. SignupView.test.js 가 그 자리를 지킨다.

const prod = (id) => ({ id, name: `상품${id}`, price: 1000, images: [] });
const GUEST_VIEWED = 'glassvue.recentlyViewed.guest';
const GUEST_SEARCH = 'glassvue.recentSearches.guest';

beforeEach(() => {
  localStorage.clear();
  clearSession();
  recentlyViewed.value = [];
  recentSearches.value = [];
});

describe('adoptGuestHistory — 가입 때 옮긴다', () => {
  it('🔴 비회원으로 쌓은 둘이 계정으로 따라온다', () => {
    pushRecentlyViewed(prod('p1'));
    pushRecentSearch('지바');

    setUser({ id: 'A' });                    // 가입 직후(계정 목록은 비어 있다)
    expect(recentlyViewed.value).toEqual([]);
    expect(recentSearches.value).toEqual([]);

    adoptGuestHistory();

    expect(recentlyViewed.value.map((p) => p.id)).toEqual(['p1']);
    expect(recentSearches.value).toEqual(['지바']);
    expect(JSON.parse(localStorage.getItem('glassvue.recentlyViewed.A'))[0].id).toBe('p1');
    expect(JSON.parse(localStorage.getItem('glassvue.recentSearches.A'))).toEqual(['지바']);
  });

  it('🔴 **옮기고 나면 guest 는 빈다** — 복사가 아니라 이동이다(다음 사람이 못 본다)', () => {
    pushRecentlyViewed(prod('p1'));
    pushRecentSearch('지바');
    setUser({ id: 'A' });

    adoptGuestHistory();

    expect(localStorage.getItem(GUEST_VIEWED)).toBeNull();
    expect(localStorage.getItem(GUEST_SEARCH)).toBeNull();
  });

  it('🔴 로그아웃해도 **guest 로 되돌아오지 않는다** — 이동이라 원본이 없다', () => {
    pushRecentlyViewed(prod('p1'));
    pushRecentSearch('지바');
    setUser({ id: 'A' });
    adoptGuestHistory();

    clearSession();

    expect(recentlyViewed.value).toEqual([]);
    expect(recentSearches.value).toEqual([]);
  });

  it('⚠ 비로그인 상태에서 부르면 **아무 일도 안 한다** — guest → guest 자기 자신 이동 방지', () => {
    pushRecentlyViewed(prod('p1'));
    pushRecentSearch('지바');

    adoptGuestHistory();                     // 계정이 없다

    expect(recentlyViewed.value.map((p) => p.id)).toEqual(['p1']);
    expect(recentSearches.value).toEqual(['지바']);
    expect(localStorage.getItem(GUEST_VIEWED)).not.toBeNull();   // 안 지웠다
  });

  it('guest 가 비어 있으면 계정 목록을 안 건드린다', () => {
    setUser({ id: 'A' });
    pushRecentSearch('내것');

    adoptGuestHistory();

    expect(recentSearches.value).toEqual(['내것']);
  });
});

describe('adoptGuestHistory — 합칠 때의 규칙 (각 스토어 것을 그대로 쓴다)', () => {
  it('계정에 있던 것이 앞, guest 것이 뒤', () => {
    pushRecentSearch('게스트');
    setUser({ id: 'A' });
    pushRecentSearch('내것');

    adoptGuestHistory();

    expect(recentSearches.value).toEqual(['내것', '게스트']);
  });

  it('⚠ 겹치면 하나로 — 검색어는 **대소문자를 무시**한다(그 스토어의 규칙이다)', () => {
    pushRecentSearch('Zibar');
    setUser({ id: 'A' });
    pushRecentSearch('zibar');

    adoptGuestHistory();

    expect(recentSearches.value).toEqual(['zibar']);   // 계정 쪽 표기가 남는다
  });

  it('⚠ 겹치면 하나로 — 본 상품은 **id** 로 본다', () => {
    pushRecentlyViewed(prod('p1'));
    setUser({ id: 'A' });
    pushRecentlyViewed(prod('p1'));

    adoptGuestHistory();

    expect(recentlyViewed.value.map((p) => p.id)).toEqual(['p1']);
  });

  it('상한을 넘으면 오래된 쪽(guest)이 밀린다 — 검색어 8개', () => {
    for (let i = 1; i <= 6; i += 1) pushRecentSearch(`게스트${i}`);
    setUser({ id: 'A' });
    for (let i = 1; i <= 5; i += 1) pushRecentSearch(`내것${i}`);

    adoptGuestHistory();

    expect(recentSearches.value).toHaveLength(8);
    expect(recentSearches.value.slice(0, 5)).toEqual(['내것5', '내것4', '내것3', '내것2', '내것1']);
    expect(recentSearches.value).not.toContain('게스트1');   // 가장 오래된 것이 밀렸다
  });

  it('⚠ 저장소가 망가져 있어도 죽지 않는다 (계정 목록을 잃지 않는다)', () => {
    localStorage.setItem(GUEST_SEARCH, '{깨진 값');
    setUser({ id: 'A' });
    pushRecentSearch('내것');

    adoptGuestHistory();

    expect(recentSearches.value).toEqual(['내것']);
  });
});
