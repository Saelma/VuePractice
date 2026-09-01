import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 상품 상세 — 🔴 **비회원에게 막다른 길이던 자리** (2026-09-01, BACKLOG J-1).
//
// 🔴 전에는 비회원 분기가 «구매하려면 로그인이 필요해요.» 라는 **누를 수 없는 `<p>` 한 줄**이었다.
//    바로 아래 찜 하트는 비회원이 눌러도 로그인으로 보내며 **돌아올 경로까지 들고 가는데**,
//    정작 **주 행동(담기)에만 길이 없었다** — 한 카드 안에서 규칙이 두 벌이었다.
//
// ⚠ 이 파일이 보는 것은 «비회원에게 갈 길이 있는가» 뿐이다. 갤러리·리뷰·문의·옵션은 스텁으로 막는다.
//    복귀 경로 규칙 자체는 composables/useLoginRedirect.test.js 가 본다.

const push = vi.fn();
const route = { path: '/products/p1', fullPath: '/products/p1?tab=review', query: {} };
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  useRoute: () => route,
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
}));
vi.mock('../api/product', async (importOriginal) => {
  // ⚠ 가격·상태 표시 함수는 **진짜를 쓴다** — 가짜로 갈아끼우면 이 화면이 그걸 쓴다는 사실이 빠진다.
  const real = await importOriginal();
  return {
    ...real,
    getProduct: () => Promise.resolve({
      id: 'p1', name: 'ZZ상품', description: 'd', price: 10_000, status: 'SELLING',
      images: [], variants: [{ id: 'v1', name: '기본', stock: 5, priceDelta: 0 }],
      category: { id: 'c1', name: 'ZZ카테고리' }, avgRating: 0, reviewCount: 0,
    }),
  };
});
vi.mock('../api/cart', () => ({ addToCart: vi.fn() }));
vi.mock('../stores/cart', () => ({ loadCartCount: vi.fn(), cartState: { count: 0 } }));

import ProductDetailView from './ProductDetailView.vue';
import { setTokens, setUser, clearSession } from '../stores/auth';

const STUBS = {
  WishlistButton: true, RestockButton: true, ProductReviews: true, ProductInquiries: true,
};

let wrapper;
async function mountView() {
  wrapper = mount(ProductDetailView, { props: { id: 'p1' }, global: { stubs: STUBS } });
  await flushPromises();
  return wrapper;
}
const btn = (w, text) => w.findAll('button').find((b) => b.text() === text);

beforeEach(() => {
  push.mockReset();
  clearSession();
  route.fullPath = '/products/p1?tab=review';
});
afterEach(() => wrapper?.unmount());

describe('ProductDetailView — 비회원 구매 동선 (J-1)', () => {
  it('🔴 비회원에게 **「로그인하고 담기」 버튼**이 있다 — 전에는 누를 수 없는 문구뿐이었다', async () => {
    const w = await mountView();
    expect(btn(w, '로그인하고 담기')).toBeTruthy();
    // 담기 자체는 여전히 없다(로그인해야 담는다) — 「될 것처럼」 보여 주지 않는다.
    expect(btn(w, '장바구니 담기')).toBeFalsy();
  });

  it('🔴 누르면 **지금 보던 상품 경로를 들고** 로그인으로 간다', async () => {
    const w = await mountView();
    await btn(w, '로그인하고 담기').trigger('click');

    expect(push).toHaveBeenCalledWith({
      path: '/login', query: { redirect: '/products/p1?tab=review' },
    });
  });

  it('로그인 회원에게는 원래대로 「장바구니 담기」다 — 유도 버튼이 남으면 안 된다', async () => {
    // ⚠ `isLoggedIn` 은 **토큰**을 본다(사용자 객체가 아니다) — setUser 만으로는 로그인이 아니다.
    setTokens('zz-access', 'zz-refresh');
    setUser({ id: 'A', role: 'USER' });
    const w = await mountView();

    expect(btn(w, '장바구니 담기')).toBeTruthy();
    expect(btn(w, '로그인하고 담기')).toBeFalsy();
  });
});
