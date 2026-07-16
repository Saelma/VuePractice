import { describe, it, expect } from 'vitest';
import { orderStatusText, orderStatusClass, ORDER_STATUS_TEXT } from './order';

describe('order status 헬퍼', () => {
  it('알려진 상태를 한글로 매핑', () => {
    expect(orderStatusText('ORDERED')).toBe('결제대기');
    expect(orderStatusText('PAID')).toBe('결제완료');
    expect(orderStatusText('SHIPPED')).toBe('발송완료');
    expect(orderStatusText('CANCELLED')).toBe('취소됨');
    expect(Object.keys(ORDER_STATUS_TEXT)).toHaveLength(4);
  });

  it('모르는 상태는 원문 그대로', () => {
    expect(orderStatusText('WEIRD')).toBe('WEIRD');
  });

  it('상태별 배지 색상이 다르고, 모르는 값은 slate 기본', () => {
    expect(orderStatusClass('ORDERED')).toContain('amber');
    expect(orderStatusClass('PAID')).toContain('blue');
    expect(orderStatusClass('SHIPPED')).toContain('green');
    expect(orderStatusClass('CANCELLED')).toContain('slate');
    expect(orderStatusClass('XXX')).toContain('slate');
  });
});
