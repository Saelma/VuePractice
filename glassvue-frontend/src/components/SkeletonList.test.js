import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import SkeletonList from './SkeletonList.vue';

// 목록형 로딩 스켈레톤. 행 수와 "오른쪽 값" 유무만 다르면 되도록 좁게 만든 컴포넌트라,
// 그 두 가지가 실제로 반영되는지만 고정한다.
describe('SkeletonList', () => {
  it('기본 3행을 그린다', () => {
    const w = mount(SkeletonList);
    expect(w.findAll('.skeleton').length).toBe(3 * 2); // 행당 막대 2개
  });

  it('rows로 행 수를 조절한다', () => {
    const w = mount(SkeletonList, { props: { rows: 6 } });
    expect(w.findAll('.skeleton').length).toBe(6 * 2);
  });

  it('trailing이면 행마다 오른쪽 막대가 하나 더 붙는다 (조회수·금액 목록용)', () => {
    const w = mount(SkeletonList, { props: { rows: 2, trailing: true } });
    expect(w.findAll('.skeleton').length).toBe(2 * 3);
  });

  it('장식이라 스크린리더에서 숨긴다', () => {
    const w = mount(SkeletonList);
    expect(w.attributes('aria-hidden')).toBe('true');
  });
});
