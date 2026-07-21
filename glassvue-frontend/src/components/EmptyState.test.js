import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import EmptyState from './EmptyState.vue';

// 7개 화면이 공유하는 빈 상태. 문구·아이콘·행동이 상황마다 달라야 하므로(필터 때문에 빈 것 vs 원래 없는 것)
// 값으로 받고 slot으로 여는 구조를 고정한다.
describe('EmptyState', () => {
  it('아이콘과 메시지를 보여준다', () => {
    const w = mount(EmptyState, { props: { icon: '🔍', message: '조건에 맞는 상품이 없어요.' } });

    expect(w.text()).toContain('🔍');
    expect(w.text()).toContain('조건에 맞는 상품이 없어요.');
  });

  it('hint는 있을 때만 렌더된다', () => {
    const without = mount(EmptyState, { props: { message: 'x' } });
    expect(without.find('.muted').exists()).toBe(false);

    const withHint = mount(EmptyState, { props: { message: 'x', hint: '먼저 등록해 보세요.' } });
    expect(withHint.text()).toContain('먼저 등록해 보세요.');
  });

  it('행동 버튼은 slot으로 넣는다 (화면마다 개수·종류가 다르므로)', () => {
    const w = mount(EmptyState, {
      props: { message: 'x' },
      slots: { default: '<button class="btn">상품 보러 가기</button>' },
    });

    expect(w.find('button.btn').text()).toBe('상품 보러 가기');
  });

  it('density=section이면 여백을 좁힌다 (리뷰·문의처럼 섹션 안에 들어갈 때)', () => {
    const page = mount(EmptyState, { props: { message: 'x' } });
    const section = mount(EmptyState, { props: { message: 'x', density: 'section' } });

    expect(page.classes()).toContain('py-16');
    expect(section.classes()).toContain('py-12');
  });

  it('아이콘은 장식이라 스크린리더에서 숨긴다', () => {
    const w = mount(EmptyState, { props: { message: 'x' } });
    expect(w.find('span').attributes('aria-hidden')).toBe('true');
  });
});
