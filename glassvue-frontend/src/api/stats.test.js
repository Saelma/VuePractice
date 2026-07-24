import { describe, it, expect } from 'vitest';
import { barHeight, shortDate } from './stats';

describe('barHeight', () => {
  it('최대값 대비 비율(%)', () => {
    expect(barHeight(50_000, 100_000)).toBe(50);
    expect(barHeight(100_000, 100_000)).toBe(100);
  });

  it('값이 0이면 0 — 매출 없는 날은 막대를 그리지 않는다', () => {
    expect(barHeight(0, 100_000)).toBe(0);
  });

  it('값이 있으면 최소 2% — 아주 적은 매출이 "없는 날"과 같아 보이면 안 된다', () => {
    expect(barHeight(1, 1_000_000)).toBe(2);
  });

  it('최대값이 0이거나 없으면 0 — 0으로 나누지 않는다', () => {
    expect(barHeight(0, 0)).toBe(0);
    expect(barHeight(100, 0)).toBe(0);
    expect(barHeight(100, undefined)).toBe(0);
  });
});

describe('shortDate', () => {
  it('yyyy-MM-dd → M/D (축이 30칸이라 앞자리 0은 자리만 차지한다)', () => {
    expect(shortDate('2026-07-24')).toBe('7/24');
    expect(shortDate('2026-01-05')).toBe('1/5');
  });

  it('형식이 아니면 그대로 — 화면이 깨지지 않게', () => {
    expect(shortDate('')).toBe('');
    expect(shortDate(undefined)).toBe('');
    expect(shortDate('이상한값')).toBe('이상한값');
  });
});
