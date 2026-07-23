import { describe, it, expect } from 'vitest';
import { statusText, priceText, STATUS_OPTIONS, hasDiscount, discountRate } from './product';

describe('product 헬퍼', () => {
  it('상태 텍스트 매핑', () => {
    expect(statusText('SELLING')).toBe('판매중');
    expect(statusText('SOLD_OUT')).toBe('품절');
    expect(statusText('HIDDEN')).toBe('숨김');
    expect(statusText('UNKNOWN')).toBe('UNKNOWN');
    expect(STATUS_OPTIONS).toHaveLength(3);
  });

  it('가격을 천단위 콤마 + 원 표기', () => {
    expect(priceText(10_000)).toBe('10,000원');
    expect(priceText(0)).toBe('0원');
    expect(priceText(1_234_567)).toBe('1,234,567원');
  });

  it('가격이 null/undefined면 빈 문자열', () => {
    expect(priceText(null)).toBe('');
    expect(priceText(undefined)).toBe('');
  });
});

describe('할인 표시 헬퍼', () => {
  // 서버는 listPrice(정가)만 주고 할인율은 화면이 계산한다 — 화면마다 갈리지 않게 여기 한 곳에만 둔다.
  it('정가가 판매가보다 클 때만 할인이다', () => {
    expect(hasDiscount({ price: 31200, listPrice: 39000 })).toBe(true);
    expect(hasDiscount({ price: 39000, listPrice: 39000 })).toBe(false); // 같으면 할인 아님
    expect(hasDiscount({ price: 39000, listPrice: 30000 })).toBe(false); // 정가가 더 싸면 할인 아님
    expect(hasDiscount({ price: 39000, listPrice: null })).toBe(false);  // 정가 없음 = 할인 없음
    expect(hasDiscount(null)).toBe(false);
  });

  it('할인율은 반올림한다', () => {
    expect(discountRate({ price: 31200, listPrice: 39000 })).toBe(20);
    expect(discountRate({ price: 10000, listPrice: 30000 })).toBe(67); // 66.67 → 67
    expect(discountRate({ price: 39000, listPrice: null })).toBe(0);
    expect(discountRate({ price: 39000, listPrice: 39000 })).toBe(0);
  });
});
