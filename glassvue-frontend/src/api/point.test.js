import { describe, it, expect } from 'vitest';
import { maxUsablePoint, clampPoint, gradeText } from './point';

describe('maxUsablePoint', () => {
  it('잔액과 상품금액 중 작은 쪽 — 서버의 상한 규칙과 같아야 한다', () => {
    expect(maxUsablePoint(10_000, 50_000)).toBe(10_000); // 잔액이 한계
    expect(maxUsablePoint(90_000, 50_000)).toBe(50_000); // 상품금액이 한계
  });

  it('쿠폰 할인을 뺀 금액이 상한 — 결제금액이 음수가 되면 안 된다', () => {
    expect(maxUsablePoint(90_000, 50_000, 20_000)).toBe(30_000);
  });

  it('쿠폰이 상품금액을 다 덮으면 쓸 수 있는 적립금은 0', () => {
    expect(maxUsablePoint(90_000, 50_000, 50_000)).toBe(0);
  });

  it('값이 없거나 음수여도 0 아래로 안 내려간다', () => {
    expect(maxUsablePoint(0, 0)).toBe(0);
    expect(maxUsablePoint(undefined, undefined)).toBe(0);
    expect(maxUsablePoint(1000, 500, 900)).toBe(0);
  });
});

describe('clampPoint', () => {
  it('상한을 넘으면 상한으로 자른다', () => {
    expect(clampPoint(99_999, 5_000)).toBe(5_000);
  });

  it('소수는 내림 — 원 단위만 쓴다', () => {
    expect(clampPoint(1500.9, 5_000)).toBe(1500);
  });

  it('음수·0·문자는 0', () => {
    expect(clampPoint(-100, 5_000)).toBe(0);
    expect(clampPoint(0, 5_000)).toBe(0);
    expect(clampPoint('abc', 5_000)).toBe(0);
    expect(clampPoint('', 5_000)).toBe(0);
  });
});

describe('gradeText', () => {
  it('등급 한글 표기', () => {
    expect(gradeText('BRONZE')).toBe('브론즈');
    expect(gradeText('VIP')).toBe('VIP');
  });

  it('모르는 값은 그대로 — 새 등급이 생겨도 화면이 안 깨진다', () => {
    expect(gradeText('PLATINUM')).toBe('PLATINUM');
    expect(gradeText(null)).toBe('');
  });
});
