import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 상품 목록 — 🔴 **이 화면에 테스트가 처음 생긴다** (2026-09-01).
//
// 🔴 **왜 지금인가**: G-7(최근 검색어)이 «검색을 실행한 순간» 을 두 자리에서 담는데
//    (헤더 폼 · 여기 필터), **그 배선을 지키는 것이 하나도 없었다.** 스토어와 표시 컴포넌트는
//    촘촘한데 **둘을 잇는 선만 비어 있었다** — G-6 이 «단위는 여섯 벌인데 통합이 0건» 이었던 것과
//    같은 모양이고, 그건 오늘 통합으로 메웠다(09-01 §4).
//
// ⚠ **여기가 유독 중요한 이유**: `sm` 미만 화면에서는 헤더 검색이 감춰져 **이 필터가 유일한 입구**다.
//    여기 배선이 끊기면 **좁은 화면 사용자는 최근 검색어가 영영 안 쌓인다** — 그리고 그건
//    «아무 일도 안 일어나는» 고장이라 **아무도 신고하지 않는다.**
//
// ⚠ 목록 규칙(중복·상한·계정 분리)은 stores/recentSearches.test.js 가, 표시 규칙은
//    components/RecentSearches.test.js 가 본다. **여기서는 «부르는가 · 그리는가» 만** 본다.

const fetchProducts = vi.fn();
vi.mock('../api/product', async (importOriginal) => {
  // ⚠ SORT_OPTIONS·STATUS_OPTIONS·priceText 는 **진짜를 쓴다**(AuditLogAdminView.test.js 와 같은 이유) —
  //    가짜로 갈아끼우면 이 화면이 그 목록을 쓴다는 사실 자체가 검증에서 빠진다.
  const real = await importOriginal();
  return { ...real, fetchProducts: (...a) => fetchProducts(...a) };
});
vi.mock('../api/category', () => ({ fetchCategories: () => Promise.resolve([]) }));
vi.mock('../stores/wishlist', () => ({
  loadWishlistIds: vi.fn(),
  isWishlisted: () => false,
  toggleWishlist: vi.fn(),
  wishlistState: { ids: new Set() },
}));

const push = vi.fn();
const route = { query: {} };
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  useRoute: () => route,
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
}));

import ProductListView from './ProductListView.vue';
import { recentSearches, pushRecentSearch } from '../stores/recentSearches';
import { clearSession } from '../stores/auth';

const emptyPage = { content: [], page: 0, totalPages: 0, totalElements: 0 };

let wrapper;

async function mountView() {
  wrapper = mount(ProductListView);
  await flushPromises();
  return wrapper;
}

/**
 * 「상품명」 칸에 실제로 입력한다.
 * ⚠ DevExtreme 은 `change` 에서 값을 확정하므로 `setValue` 만으로는 v-model 이 안 움직인다
 * (AuditLogAdminView.test.js 가 실측으로 남겨 둔 것 — 그대로 따른다).
 */
async function typeName(w, value) {
  const input = w.find('input[placeholder="검색어"]');
  await input.setValue(value);
  await input.trigger('change');
  await flushPromises();
}

async function clickApply(w) {
  await w.findAll('button').find((b) => b.text() === '적용').trigger('click');
  await flushPromises();
}

beforeEach(() => {
  fetchProducts.mockReset();
  fetchProducts.mockResolvedValue(emptyPage);
  push.mockReset();
  route.query = {};
  localStorage.clear();
  clearSession();
  recentSearches.value = [];
});
afterEach(() => wrapper?.unmount());

describe('ProductListView — 최근 검색어 배선 (G-7)', () => {
  it('🔴 필터로 검색하면 최근 검색어에 담긴다 — 좁은 화면에선 여기가 유일한 입구다', async () => {
    const w = await mountView();
    await typeName(w, '지바');
    await clickApply(w);

    expect(recentSearches.value).toEqual(['지바']);
    // 실제로 그 조건으로 목록을 다시 불렀는지도 함께 본다(담기만 하고 검색이 안 되면 반쪽이다).
    expect(fetchProducts).toHaveBeenLastCalledWith(expect.objectContaining({ name: '지바' }));
  });

  it('⚠ 검색어가 비었으면 안 담는다 — 「적용」은 가격·상태만 바꾸는 데도 쓴다', async () => {
    const w = await mountView();
    await clickApply(w);
    expect(recentSearches.value).toEqual([]);
  });

  it('앞뒤 공백만 친 것도 안 담는다', async () => {
    const w = await mountView();
    await typeName(w, '   ');
    await clickApply(w);
    expect(recentSearches.value).toEqual([]);
  });

  it('🔴 URL 로 들어온 검색어는 안 담는다 — 남이 공유한 링크가 내 검색어가 되면 안 된다', async () => {
    route.query = { name: '몽쉘' };
    const w = await mountView();

    // 화면은 그 조건으로 목록을 부른다(?name= 은 살아 있다)…
    expect(fetchProducts).toHaveBeenCalledWith(expect.objectContaining({ name: '몽쉘' }));
    // …그런데 «내가 친 것» 이 아니므로 최근 검색어는 비어 있어야 한다.
    expect(recentSearches.value).toEqual([]);
    expect(w.text()).not.toContain('최근 검색어');
  });
});

describe('ProductListView — 최근 검색어 표시 (G-7)', () => {
  it('비어 있으면 안 그린다', async () => {
    const w = await mountView();
    expect(w.text()).not.toContain('최근 검색어');
  });

  it('담긴 것이 있으면 필터 안에 **인라인으로** 선다 (헤더처럼 떠 있지 않다)', async () => {
    pushRecentSearch('지바');
    const w = await mountView();
    expect(w.text()).toContain('최근 검색어');
    expect(w.text()).toContain('지바');
  });

  it('최근 검색어를 누르면 그 말로 **바로** 적용된다 — 「적용」을 또 누르게 하지 않는다', async () => {
    pushRecentSearch('반팔티');
    const w = await mountView();
    fetchProducts.mockClear();

    const term = w.findAll('button').find((b) => b.text() === '반팔티');
    await term.trigger('click');
    await flushPromises();

    expect(fetchProducts).toHaveBeenLastCalledWith(expect.objectContaining({ name: '반팔티' }));
    expect(w.find('input[placeholder="검색어"]').element.value).toBe('반팔티');
  });
});
