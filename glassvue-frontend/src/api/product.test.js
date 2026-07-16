import { describe, it, expect } from 'vitest';
import { statusText, priceText, STATUS_OPTIONS } from './product';

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
