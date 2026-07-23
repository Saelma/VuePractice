import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ItemThumb from './ItemThumb.vue';

// 주문 품목 썸네일. 이 컴포넌트가 존재하는 이유는 **이미지 로드 실패가 정상 흐름**이기 때문이다 —
// 주문 품목의 이미지 URL은 주문 시점 스냅샷이라, 이후 상품이 삭제되면 파일이 정리돼 404가 된다.
// 그래서 "실패하면 대체 표시로 넘어가는가"와 "src가 바뀌면 다시 시도하는가"를 계약으로 고정한다.
describe('ItemThumb', () => {
  it('src가 있으면 이미지를 그린다', () => {
    const w = mount(ItemThumb, { props: { src: '/uploads/a_t.webp', alt: '몽쉘' } });

    const img = w.find('img');
    expect(img.exists()).toBe(true);
    expect(img.attributes('src')).toBe('/uploads/a_t.webp');
    expect(img.attributes('alt')).toBe('몽쉘');
  });

  it('src가 없으면 대체 표시를 그린다 (배송지 도입 이전 주문처럼 스냅샷이 없는 경우)', () => {
    const w = mount(ItemThumb);

    expect(w.find('img').exists()).toBe(false);
    expect(w.text()).toContain('🖼️');
  });

  // 이 컴포넌트의 핵심. 404여도 화면이 깨지지 않아야 한다.
  it('이미지 로드에 실패하면 대체 표시로 넘어간다 (상품 삭제로 파일이 정리된 경우)', async () => {
    const w = mount(ItemThumb, { props: { src: '/uploads/사라진파일_t.webp' } });
    expect(w.find('img').exists()).toBe(true);

    await w.find('img').trigger('error');

    expect(w.find('img').exists()).toBe(false);
    expect(w.text()).toContain('🖼️');
  });

  // 실패 상태가 남아 있으면, 다른 품목을 그릴 때도 계속 대체 표시가 뜬다.
  // 목록에서 컴포넌트가 재사용되므로 src가 바뀌면 반드시 다시 시도해야 한다.
  it('src가 바뀌면 실패 상태를 초기화하고 다시 시도한다', async () => {
    const w = mount(ItemThumb, { props: { src: '/uploads/깨진것_t.webp' } });
    await w.find('img').trigger('error');
    expect(w.find('img').exists()).toBe(false);

    await w.setProps({ src: '/uploads/멀쩡한것_t.webp' });

    const img = w.find('img');
    expect(img.exists()).toBe(true);
    expect(img.attributes('src')).toBe('/uploads/멀쩡한것_t.webp');
  });

  it('size로 크기 클래스를 바꾼다 (목록과 상세의 썸네일 크기가 다르다)', () => {
    const dflt = mount(ItemThumb, { props: { src: '/a.webp' } });
    expect(dflt.classes()).toContain('h-14');
    expect(dflt.classes()).toContain('w-14');

    const big = mount(ItemThumb, { props: { src: '/a.webp', size: 'h-20 w-20' } });
    expect(big.classes()).toContain('h-20');
    expect(big.classes()).toContain('w-20');
  });

  it('대체 표시는 장식이라 스크린리더에서 숨긴다', () => {
    const w = mount(ItemThumb);
    expect(w.find('[aria-hidden="true"]').exists()).toBe(true);
  });
});
