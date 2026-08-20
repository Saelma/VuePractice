import { describe, it, expect } from 'vitest';
import {
  statusText, priceText, STATUS_OPTIONS, hasDiscount, discountRate,
  stockReasonText, stockDeltaText, strikePrice,
} from './product';

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

describe('재고 이력 헬퍼 (B-19)', () => {
  it('사유를 한국어로 매핑하되, 모르는 값은 **원문 그대로** 돌려준다', () => {
    expect(stockReasonText('ORDER')).toBe('주문');
    expect(stockReasonText('CANCEL')).toBe('주문 취소');
    expect(stockReasonText('RETURN')).toBe('반품 승인');
    expect(stockReasonText('ADMIN_CREATE')).toBe('등록');
    expect(stockReasonText('ADMIN_EDIT')).toBe('관리자 편집');
    // 서버 enum 에 값이 늘어도 칸이 비지 않아야 한다 — 빈칸은 "사유가 없다"로 읽힌다.
    expect(stockReasonText('SOMETHING_NEW')).toBe('SOMETHING_NEW');
    expect(stockReasonText(null)).toBe('');
  });

  it('변동량은 **부호를 항상** 붙인다 (+3 / -3)', () => {
    expect(stockDeltaText(3)).toBe('+3');
    expect(stockDeltaText(-3)).toBe('-3');
    expect(stockDeltaText(1234)).toBe('+1,234');
    expect(stockDeltaText(-1234)).toBe('-1,234');
  });
});

/**
 * 🔴 **주문 스냅샷의 세일 흔적** (2026-08-20, BACKLOG G-9).
 *
 * 주문 항목에는 `discountRate` 가 없다 — 산 시점의 값만 있다. 그래서 «세일로 샀나» 는
 * `regularPrice > price` 로만 알 수 있고, **그 갈래가 없으면 정가 칸이 빈 상품을 세일가로 산 주문에
 * 흔적이 아예 안 남는다**(실측 2026-08-20, `20260820-4733`: price 9,600 · listPrice NULL).
 */
describe('주문 스냅샷의 세일 흔적 (G-9)', () => {
  // 실측 표본과 같은 모양: 정가 칸은 비었고 기간 세일로 12,000 → 9,600 에 샀다.
  const soldOnSale = { price: 9600, regularPrice: 12000, listPrice: null };

  it('🔴 정가가 없어도 세일로 샀으면 할인으로 읽는다', () => {
    expect(hasDiscount(soldOnSale)).toBe(true);
    expect(strikePrice(soldOnSale)).toBe(12000);
    expect(discountRate(soldOnSale)).toBe(20);
  });

  it('세일 없이 샀으면 아무 줄도 안 긋는다 — regularPrice 가 price 와 같다', () => {
    const plain = { price: 10000, regularPrice: 10000, listPrice: null };
    expect(hasDiscount(plain)).toBe(false);
    expect(strikePrice(plain)).toBeNull();
  });

  it('⚠ regularPrice 가 없는 옛 주문은 정가 갈래를 그대로 탄다 (백필 안 했다)', () => {
    // null 은 «세일이 없었다» 가 아니라 «모른다» 다 — 추측해서 그리지 않는다.
    const old = { price: 31200, listPrice: 39000 };
    expect(hasDiscount(old)).toBe(true);
    expect(strikePrice(old)).toBe(39000);
    expect(discountRate(old)).toBe(20);
  });

  it('🔴 비율은 **긋는 값과 같은 기준**으로 센다 — 둘이 갈리면 화면이 스스로 모순된다', () => {
    // 세일(12,000→9,600 = 20%)과 정가(20,000)가 함께 있는 경우.
    // 취소선은 세일 전 판매가라, 비율도 그 기준이어야 한다(정가 기준이면 52% 가 된다).
    const both = { price: 9600, regularPrice: 12000, listPrice: 20000 };
    expect(strikePrice(both)).toBe(12000);
    expect(discountRate(both)).toBe(20);
  });
});
