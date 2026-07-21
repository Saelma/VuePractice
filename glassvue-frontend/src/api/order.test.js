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

  // 색을 직접 쓰지 않고 공용 badge 변형을 돌려준다(DESIGN.md §5) — 화면마다 매핑이 갈리지 않게.
  it('상태별 배지 변형이 다르고, 모르는 값은 neutral 기본', () => {
    expect(orderStatusClass('ORDERED')).toBe('badge-warning');
    expect(orderStatusClass('PAID')).toBe('badge-success');
    expect(orderStatusClass('SHIPPED')).toBe('badge-neutral');
    expect(orderStatusClass('CANCELLED')).toBe('badge-danger');
    expect(orderStatusClass('XXX')).toBe('badge-neutral');
  });
});
