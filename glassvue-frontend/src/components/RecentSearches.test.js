import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import RecentSearches from './RecentSearches.vue';
import { recentSearches, pushRecentSearch } from '../stores/recentSearches';
import { clearSession } from '../stores/auth';

// 최근 검색어 목록 컴포넌트 (2026-09-01, BACKLOG G-7).
//
// 🔴 **이 파일이 지키는 것은 «표시 규칙이 한 벌» 이라는 것이다.** 검색 입구가 둘이라 이 목록도
//    두 자리(헤더 드롭다운 · 목록 필터)에 서는데, 둘이 각자 그리면 «한쪽에만 ✕ 가 있다» 로 갈린다.
//    그래서 규칙을 여기서만 확인하고, 부르는 쪽은 «감싸는 것» 만 한다.
//
// ⚠ 값이 맞는지(중복 제거·상한·계정 분리)는 stores/recentSearches.test.js 가 본다. 여기선 안 겹친다.

let wrapper;

beforeEach(() => {
  localStorage.clear();
  clearSession();
  recentSearches.value = [];
});
afterEach(() => wrapper?.unmount());

describe('RecentSearches', () => {
  it('🔴 비었으면 아무것도 안 그린다 — 부르는 쪽이 «열지 말지» 를 정할 수 있어야 한다', () => {
    wrapper = mount(RecentSearches);
    expect(wrapper.text()).toBe('');
    expect(wrapper.find('ul').exists()).toBe(false);
  });

  it('최신이 위로 오게 그린다', () => {
    pushRecentSearch('지바');
    pushRecentSearch('몽쉘');
    wrapper = mount(RecentSearches);
    const rows = wrapper.findAll('li');
    expect(rows).toHaveLength(2);
    expect(rows[0].text()).toContain('몽쉘');
    expect(rows[1].text()).toContain('지바');
  });

  it('말을 누르면 pick 으로 그 말을 넘긴다 — 무엇을 할지는 부르는 쪽이 정한다', async () => {
    pushRecentSearch('지바');
    wrapper = mount(RecentSearches);
    await wrapper.find('li button').trigger('click');
    expect(wrapper.emitted('pick')).toBeTruthy();
    expect(wrapper.emitted('pick')[0]).toEqual(['지바']);
  });

  it('⚠ ✕ 는 그 줄만 지운다 — 나머지는 남는다(오타 하나 때문에 목록을 다 버리지 않게)', async () => {
    pushRecentSearch('지바');
    pushRecentSearch('몽쉘');
    wrapper = mount(RecentSearches);

    const remove = wrapper.findAll('button').find((b) => b.attributes('aria-label')?.includes('몽쉘'));
    await remove.trigger('click');

    expect(recentSearches.value).toEqual(['지바']);
    expect(wrapper.findAll('li')).toHaveLength(1);
  });

  it('✕ 는 pick 을 내지 않는다 — 지우려다 검색되면 안 된다', async () => {
    pushRecentSearch('지바');
    wrapper = mount(RecentSearches);
    const remove = wrapper.findAll('button').find((b) => b.attributes('aria-label')?.includes('지바'));
    await remove.trigger('click');
    expect(wrapper.emitted('pick')).toBeFalsy();
  });

  it('「전체 지우기」를 누르면 목록이 사라진다', async () => {
    pushRecentSearch('지바');
    pushRecentSearch('몽쉘');
    wrapper = mount(RecentSearches);

    await wrapper.findAll('button').find((b) => b.text() === '전체 지우기').trigger('click');

    expect(recentSearches.value).toEqual([]);
    expect(wrapper.find('ul').exists()).toBe(false);   // 비면 통째로 사라진다
  });
});
