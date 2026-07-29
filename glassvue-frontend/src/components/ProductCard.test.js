import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ProductCard from './ProductCard.vue';

/**
 * 카드 높이 정렬 (2026-07-29 수정 회귀 방지).
 *
 * ⚠ jsdom 에는 레이아웃이 없어 **픽셀 높이는 못 잰다.** 대신 높이를 흔들던 원인이
 * 구조적으로 제거됐는지를 본다:
 *   ① 가격줄이 `mt-auto` 로 바닥에 붙는가 (위 내용이 몇 줄이든 무관해지는 장치)
 *   ② 카드가 `h-full flex-col` 이라 그리드 행 높이에 맞춰 늘어나는가
 *   ③ 품절 배지가 **텍스트 흐름이 아니라 이미지 오버레이**에 있는가
 *
 * 실제로 눈으로 확인한 증상: 태그라인 있는 상품 / 품절 상품 / 아무것도 없는 상품이
 * 순서대로 세로 길이가 달랐다(사용자 보고).
 */
const stubs = {
  WishlistButton: true,
  StarRating: true,
  RouterLink: true,
};

function product(over = {}) {
  return {
    id: 'p1',
    name: '몽쉘',
    categoryName: '과자',
    price: 10000,
    listPrice: null,
    tagline: null,
    status: 'SELLING',
    soldOut: false,
    averageRating: 0,
    reviewCount: 0,
    images: [],
    ...over,
  };
}

function mountCard(over) {
  return mount(ProductCard, {
    props: { product: product(over) },
    global: { stubs, mocks: { $router: { push() {} } } },
  });
}

describe('ProductCard — 높이 정렬', () => {
  it('카드는 h-full + flex-col 이라 그리드 행 높이에 맞춰 늘어난다', () => {
    const btn = mountCard().find('button');
    expect(btn.classes()).toEqual(expect.arrayContaining(['h-full', 'flex', 'flex-col']));
  });

  it('가격·별점 줄은 mt-auto 로 항상 카드 바닥에 붙는다', () => {
    const w = mountCard();
    expect(w.html()).toContain('mt-auto');
  });

  it('⚠ 태그라인이 있든 없든 가격줄의 mt-auto 는 유지된다 (위 내용 줄 수와 무관)', () => {
    expect(mountCard({ tagline: null }).html()).toContain('mt-auto');
    expect(mountCard({ tagline: '매일 쓰기 좋은 기본형' }).html()).toContain('mt-auto');
  });

  it('태그라인은 있을 때만 그려진다', () => {
    expect(mountCard({ tagline: null }).text()).not.toContain('매일 쓰기 좋은');
    expect(mountCard({ tagline: '매일 쓰기 좋은 기본형' }).text()).toContain('매일 쓰기 좋은 기본형');
  });

  it('⚠ 품절은 이미지 오버레이로 그린다 — 텍스트 흐름에 배지를 넣지 않는다', () => {
    const w = mountCard({ soldOut: true });
    expect(w.text()).toContain('품절');
    // 오버레이는 이미지 컨테이너(aspect-square) 안에 absolute inset-0 으로 있다
    const overlay = w.find('.aspect-square .absolute.inset-0');
    expect(overlay.exists()).toBe(true);
    expect(overlay.text()).toContain('품절');
  });

  it('판매중지 상태도 같은 오버레이를 쓴다', () => {
    const w = mountCard({ status: 'STOPPED' });
    expect(w.find('.aspect-square .absolute.inset-0').exists()).toBe(true);
  });

  it('판매중이고 재고가 있으면 오버레이가 없다', () => {
    expect(mountCard().find('.aspect-square .absolute.inset-0').exists()).toBe(false);
  });
});
