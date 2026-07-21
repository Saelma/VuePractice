import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import ImageUploader from './ImageUploader.vue';
import { uploadImage } from '../api/image';

vi.mock('../api/image', () => ({ uploadImage: vi.fn() }));

// ImageUploader는 DevExtreme을 쓰지 않는 순수 컴포넌트라 마운트 테스트가 가능하다.
// 핵심 로직은 "최대 장수 초과분을 업로드 전에 잘라내는 것" — 올린 뒤 거절하면 서버에 고아 이미지가 남는다.
describe('ImageUploader', () => {
  const png = (name) => new File(['x'], name, { type: 'image/png' });
  const img = (id) => ({ id, url: `/uploads/${id}.png`, mediumUrl: `/uploads/${id}_m.webp`, thumbUrl: `/uploads/${id}_t.webp` });

  /** jsdom에서는 input.files를 직접 못 넣어서 정의해 준 뒤 change를 쏜다. */
  async function selectFiles(wrapper, files) {
    const input = wrapper.find('input[type="file"]');
    Object.defineProperty(input.element, 'files', { value: files, configurable: true });
    await input.trigger('change');
  }

  beforeEach(() => {
    vi.mocked(uploadImage).mockReset();
  });

  it('기존 이미지를 thumbUrl로 미리보기하고 각각 삭제 버튼을 준다', () => {
    const w = mount(ImageUploader, { props: { modelValue: [img('a'), img('b')] } });

    const imgs = w.findAll('img');
    expect(imgs).toHaveLength(2);
    expect(imgs[0].attributes('src')).toBe('/uploads/a_t.webp'); // 원본이 아니라 썸네일
    expect(w.findAll('button')).toHaveLength(2);
  });

  it('삭제 버튼 → 그 항목만 뺀 배열을 emit한다', async () => {
    const w = mount(ImageUploader, { props: { modelValue: [img('a'), img('b'), img('c')] } });

    await w.findAll('button')[1].trigger('click');

    expect(w.emitted('update:modelValue')[0][0].map((i) => i.id)).toEqual(['a', 'c']);
  });

  it('최대 장수를 채우면 input을 막고 안내를 띄운다', () => {
    const w = mount(ImageUploader, { props: { modelValue: [img('a'), img('b')], max: 2 } });

    expect(w.find('input[type="file"]').attributes('disabled')).toBeDefined();
    expect(w.text()).toContain('최대 2장까지 첨부했어요');
  });

  it('업로드 성공 → 기존 + 신규를 합쳐 emit한다', async () => {
    vi.mocked(uploadImage).mockResolvedValueOnce(img('new'));
    const w = mount(ImageUploader, { props: { modelValue: [img('a')], max: 5 } });

    await selectFiles(w, [png('n.png')]);
    await new Promise((r) => setTimeout(r)); // 업로드 await 처리

    expect(uploadImage).toHaveBeenCalledTimes(1);
    expect(w.emitted('update:modelValue')[0][0].map((i) => i.id)).toEqual(['a', 'new']);
  });

  it('남은 자리보다 많이 고르면 초과분을 잘라내고 경고한다 (업로드 전에 자른다)', async () => {
    vi.mocked(uploadImage).mockResolvedValue(img('x'));
    const w = mount(ImageUploader, { props: { modelValue: [img('a')], max: 2 } }); // 남은 자리 1

    await selectFiles(w, [png('1.png'), png('2.png'), png('3.png')]);
    await new Promise((r) => setTimeout(r));

    expect(w.emitted('error')[0][0]).toContain('1장만 첨부');
    expect(uploadImage).toHaveBeenCalledTimes(1); // 초과분은 아예 안 올린다
  });

  it('가득 찬 상태에서는 파일 선택 자체가 일어나지 않는다 (input이 비활성)', async () => {
    const w = mount(ImageUploader, { props: { modelValue: [img('a'), img('b')], max: 2 } });

    // input이 disabled라 change 이벤트가 발생하지 않는다 → 업로드도, 상태 변경도 없다.
    await selectFiles(w, [png('1.png')]);

    expect(uploadImage).not.toHaveBeenCalled();
    expect(w.emitted('update:modelValue')).toBeUndefined();
  });

  it('업로드 실패 → 에러 메시지를 올리고 목록은 안 바꾼다', async () => {
    vi.mocked(uploadImage).mockRejectedValueOnce(new Error('용량이 너무 큽니다'));
    const w = mount(ImageUploader, { props: { modelValue: [], max: 5 } });

    await selectFiles(w, [png('big.png')]);
    await new Promise((r) => setTimeout(r));

    expect(w.emitted('error')[0][0]).toBe('용량이 너무 큽니다');
    expect(w.emitted('update:modelValue')).toBeUndefined();
  });
});
