import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 헤더(App.vue) — 🔴 **앱 껍데기에 테스트가 처음 생긴다** (2026-09-01).
//
// 🔴 **왜 지금인가**: G-7(최근 검색어)의 배선이 여기와 ProductListView 두 곳에 있는데
//    **둘 다 지키는 것이 없었다.** 스토어(14건)와 표시 컴포넌트(6건)는 촘촘한데
//    «두 입구가 실제로 부르는가» 만 비어 있었다 — 09-01 §5-4 가 적어 둔 자리다.
//
// ⚠ **여기서 보는 것은 헤더의 «검색» 뿐이다.** 알림·장바구니·계정 메뉴는 각자 자기 컴포넌트가
//    있고, 여기선 **스텁으로 막는다** — 안 그러면 App 마운트가 알림 스토어·SSE 까지 끌고 온다
//    (실측: 스텁 없이는 NotificationToaster 에서 죽는다).
//
// ⚠ 목록 규칙은 stores/recentSearches.test.js, 표시 규칙은 components/RecentSearches.test.js.
//    **여기서는 «담는가 · 펴지는가 · 눌러서 검색되는가» 만** 본다.

const push = vi.fn();
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  useRoute: () => ({ path: '/', query: {} }),
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
  RouterView: { name: 'RouterView', template: '<div />' },
}));
vi.mock('./api/auth', () => ({ loadMe: vi.fn(), logout: vi.fn() }));
vi.mock('./stores/notifications', () => ({
  connectNotifications: vi.fn(),
  disconnectNotifications: vi.fn(),
}));
vi.mock('./stores/cart', () => ({
  cartState: { count: 0 },
  loadCartCount: vi.fn(),
  clearCartCount: vi.fn(),
}));

import App from './App.vue';
import { recentSearches, pushRecentSearch } from './stores/recentSearches';
import { clearSession } from './stores/auth';

const STUBS = {
  NotificationBell: true,
  NotificationToaster: true,
  AdminMenu: true,
  AccountMenu: true,
};

let wrapper;

async function mountApp() {
  wrapper = mount(App, { global: { stubs: STUBS } });
  await flushPromises();
  return wrapper;
}

const searchInput = (w) => w.find('input[placeholder="상품 검색"]');
/**
 * 🔴 **패널은 «목록(ul)» 이 아니라 «상자» 로 판정한다.**
 * ⚠ 되돌려서 확인하다 알았다: `ul` 로 보면 «비어 있는데 상자만 뜬» 경우를 **못 가른다**
 * (RecentSearches 는 비면 스스로 아무것도 안 그리므로 ul 이 없다). 그런데 DESIGN.md 가 금지하는 것이
 * 바로 그 «빈 상자» 다 — 테두리·그림자만 뜬 채로.
 */
const panel = (w) => w.find('form[role="search"] .shadow-lift');
/**
 * 바깥 클릭용 오버레이. ⚠ **이게 더 아픈 쪽이다** — 빈 상자는 보기 흉한 정도지만
 * 오버레이는 화면 전체를 덮어 **다음 클릭을 삼킨다.**
 */
const overlay = (w) => w.find('form[role="search"] .fixed.inset-0');

async function search(w, term) {
  await searchInput(w).setValue(term);
  await w.find('form[role="search"]').trigger('submit');
  await flushPromises();
}

beforeEach(() => {
  push.mockReset();
  localStorage.clear();
  clearSession();
  recentSearches.value = [];
});
afterEach(() => wrapper?.unmount());

describe('App 헤더 검색 — 최근 검색어 배선 (G-7)', () => {
  it('🔴 검색하면 최근 검색어에 담기고, 상품 목록으로 넘긴다', async () => {
    const w = await mountApp();
    await search(w, '지바');

    expect(recentSearches.value).toEqual(['지바']);
    expect(push).toHaveBeenLastCalledWith({ path: '/products', query: { name: '지바' } });
  });

  it('⚠ 빈 검색은 안 담는다 — 그건 「전체 목록」이라 검색어가 아니다', async () => {
    const w = await mountApp();
    await search(w, '   ');

    expect(recentSearches.value).toEqual([]);
    // 그래도 이동은 한다(예전부터의 동작이라 G-7 이 바꾸지 않는다).
    expect(push).toHaveBeenLastCalledWith({ path: '/products' });
  });

  it('앞뒤 공백은 떼고 담는다', async () => {
    const w = await mountApp();
    await search(w, '  몽쉘  ');
    expect(recentSearches.value).toEqual(['몽쉘']);
  });
});

describe('App 헤더 검색 — 패널이 펴지는 규칙 (G-7)', () => {
  it('🔴 목록이 비어 있으면 포커스해도 안 펴진다 — 빈 상자는 「고장」으로 읽힌다', async () => {
    const w = await mountApp();
    await searchInput(w).trigger('focus');
    await flushPromises();

    expect(panel(w).exists()).toBe(false);
    // 🔴 오버레이도 안 깔려야 한다 — 깔리면 헤더 아래 화면 전체가 «클릭이 안 먹는» 상태가 된다.
    expect(overlay(w).exists()).toBe(false);
    expect(w.text()).not.toContain('최근 검색어');
  });

  it('담긴 것이 있으면 포커스했을 때 펴진다', async () => {
    pushRecentSearch('지바');
    const w = await mountApp();
    expect(panel(w).exists()).toBe(false);   // 포커스 전에는 안 보인다

    await searchInput(w).trigger('focus');
    await flushPromises();

    expect(panel(w).exists()).toBe(true);
    expect(w.text()).toContain('최근 검색어');
    expect(w.text()).toContain('지바');
  });

  it('검색하고 나면 패널이 닫힌다 — 결과를 보러 가는데 목록이 덮고 있으면 안 된다', async () => {
    pushRecentSearch('지바');
    const w = await mountApp();
    await searchInput(w).trigger('focus');
    await flushPromises();
    expect(panel(w).exists()).toBe(true);

    await search(w, '몽쉘');
    expect(panel(w).exists()).toBe(false);
  });

  it('Esc 로 닫는다', async () => {
    pushRecentSearch('지바');
    const w = await mountApp();
    await searchInput(w).trigger('focus');
    await flushPromises();
    expect(panel(w).exists()).toBe(true);

    await searchInput(w).trigger('keydown', { key: 'Escape' });
    await flushPromises();
    expect(panel(w).exists()).toBe(false);
  });
});

describe('App 헤더 검색 — 최근 검색어를 누르면 (G-7)', () => {
  it('🔴 그 말로 검색되고, 목록에서 맨 위로 올라온다', async () => {
    pushRecentSearch('지바');
    pushRecentSearch('몽쉘');
    const w = await mountApp();
    await searchInput(w).trigger('focus');
    await flushPromises();

    const term = w.findAll('button').find((b) => b.text() === '지바');
    await term.trigger('click');
    await flushPromises();

    expect(push).toHaveBeenLastCalledWith({ path: '/products', query: { name: '지바' } });
    // 다시 친 것과 같게 취급한다 — 눌러서 쓴 말이 목록 아래로 밀려나면 다음에 또 찾아야 한다.
    expect(recentSearches.value).toEqual(['지바', '몽쉘']);
    expect(panel(w).exists()).toBe(false);   // 누르면 닫힌다
  });

  it('⚠ ✕ 로 지운 것은 검색되지 않는다 — 지우려다 이동하면 안 된다', async () => {
    pushRecentSearch('지바');
    const w = await mountApp();
    await searchInput(w).trigger('focus');
    await flushPromises();

    const remove = w.findAll('button').find((b) => b.attributes('aria-label')?.includes('지바'));
    await remove.trigger('click');
    await flushPromises();

    expect(push).not.toHaveBeenCalled();
    expect(recentSearches.value).toEqual([]);
    expect(panel(w).exists()).toBe(false);   // 비면 패널째 사라진다
  });
});
