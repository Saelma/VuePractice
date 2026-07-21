import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import StarRating from './StarRating.vue';

// StarRating은 DevExtreme을 쓰지 않는 순수 컴포넌트라 마운트 테스트가 가능하다.
// 표시 분기(리뷰 없음 / 평균 소수 / 정수 별점)와 입력(클릭 → v-model)을 고정한다.
describe('StarRating', () => {
  it('리뷰 0개(read-only)면 "리뷰 없음"을 보여준다 — 평균 0점과 구분', () => {
    const w = mount(StarRating, { props: { modelValue: 0, count: 0 } });
    expect(w.text()).toContain('리뷰 없음');
    expect(w.text()).not.toContain('★');
  });

  it('평균(소수)은 "★ 4.5"와 개수를 함께 보여준다', () => {
    const w = mount(StarRating, { props: { modelValue: 4.5, count: 2 } });
    expect(w.text()).toContain('★ 4.5');
    expect(w.text()).toContain('(2)');
  });

  it('정수 별점은 채운 별 n개 + 빈 별 (5-n)개', () => {
    const w = mount(StarRating, { props: { modelValue: 4, count: 10 } });
    // 채운 별(★) 4개 + 빈 별(★) 1개 = 총 5개
    expect((w.text().match(/★/g) || []).length).toBe(5);
    expect(w.text()).toContain('(10)');
  });

  it('count를 안 넘기면 개수 표기가 없다', () => {
    const w = mount(StarRating, { props: { modelValue: 3 } });
    expect(w.text()).not.toContain('(');
  });

  it('editable이면 별 5개(radio)를 그리고, 클릭하면 그 점수를 emit한다', async () => {
    const w = mount(StarRating, { props: { modelValue: 0, editable: true } });
    const stars = w.findAll('button[role="radio"]');
    expect(stars).toHaveLength(5);

    await stars[2].trigger('click'); // 3번째 별
    expect(w.emitted('update:modelValue')).toBeTruthy();
    expect(w.emitted('update:modelValue')[0]).toEqual([3]);
  });

  it('editable에서 현재 값 이하의 별만 채워진다(색상 클래스)', () => {
    const w = mount(StarRating, { props: { modelValue: 2, editable: true } });
    const stars = w.findAll('button[role="radio"]');
    expect(stars[0].classes()).toContain('text-amber-400'); // 1 <= 2
    expect(stars[1].classes()).toContain('text-amber-400'); // 2 <= 2
    expect(stars[2].classes()).toContain('text-slate-300'); // 3 > 2
    expect(stars[1].attributes('aria-checked')).toBe('true');
  });
});
