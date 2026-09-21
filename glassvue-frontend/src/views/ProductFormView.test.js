import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 상품 등록·수정 폼의 첫 뷰 테스트 (2026-09-21).
//
// 🔴 **만든 이유가 둘이다.**
//   ① 08-14 이월이 이 화면을 *"어제 **정가 칸 사고가 난 화면**"* 으로 지목했는데 **뷰 테스트가 없었다.**
//   ② 조건부 잔여의 «정가 칸이 왜 빈 입력을 0 으로 되돌리는지 모른다»(08-13 §13-1-1)를
//      **브라우저 없이 재현해 보려고** 만들었다. ⚠ 재현되든 안 되든 **이 파일은 남는다.**
//
// ⚠ **덮는 범위**: 정가 칸의 «비움» 과 저장 직전 검증만 본다. 이미지·옵션·수정 모드는 여기서 안 본다.

const push = vi.fn();
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: {} }),
  useRouter: () => ({ push: (...a) => push(...a) }),
  RouterLink: { template: '<a><slot /></a>' },
}));

const createProduct = vi.fn();
const updateProduct = vi.fn();
const getProduct = vi.fn();

// ⚠ `priceText`·`STATUS_OPTIONS` 는 **진짜를 쓴다** — 화면이 그것으로 그리고, 가짜로 갈면
//    「무엇을 보여 주나」가 검증 대상에서 빠진다(OrderDetailView.test.js 가 같은 판단을 했다).
vi.mock('../api/product', async (importOriginal) => ({
  ...(await importOriginal()),
  getProduct: (...a) => getProduct(...a),
  createProduct: (...a) => createProduct(...a),
  updateProduct: (...a) => updateProduct(...a),
}));
vi.mock('../api/category', () => ({
  fetchCategories: () => Promise.resolve([{ id: 'c1', name: 'ZZ카테고리' }]),
}));

import { DxSelectBox } from 'devextreme-vue/select-box';
import { DxNumberBox } from 'devextreme-vue/number-box';
import ProductFormView from './ProductFormView.vue';

/** 라벨 글자로 칸을 찾는다 — 순서(index)로 찾으면 칸이 하나 늘 때 조용히 다른 칸을 본다. */
function inputFor(w, labelText) {
  const label = w.findAll('label.field').find((l) => l.text().includes(labelText));
  if (!label) throw new Error(`라벨을 못 찾았다: ${labelText}`);
  const input = label.find('input.dx-texteditor-input');
  if (!input.exists()) throw new Error(`그 라벨 아래 입력칸이 없다: ${labelText}`);
  return input;
}

/** DevExtreme 은 `change` 로 값을 확정한다(SignupView.test.js 와 같은 방식). */
async function type(input, value) {
  await input.setValue(value);
  await input.trigger('change');
}

let w;

async function open() {
  createProduct.mockReset().mockResolvedValue('new-id');
  push.mockReset();
  w = mount(ProductFormView, {
    props: { id: null },
    global: { stubs: { ImageUploader: true, StockHistoryPanel: true, RouterLink: true } },
  });
  await flushPromises();
  return w;
}

/** 저장까지 미는 데 필요한 나머지 칸을 채운다(정가는 건드리지 않는다). */
async function fillRequired(w) {
  await type(inputFor(w, '상품명'), 'ZZP-폼검증');
  await type(inputFor(w, '판매가(원)'), '10000');
  await type(inputFor(w, '옵션명'), '기본');
  await type(inputFor(w, '재고'), '5');
  // 설명은 DxTextArea 라 textarea 다.
  const area = w.find('textarea');
  await area.setValue('설명');
  await area.trigger('change');
  // 카테고리는 SelectBox — 목록 팝업은 jsdom 에서 안 열린다(TROUBLESHOOTING 「팝업 위젯」).
  // 그래서 **컴포넌트에 값을 직접 emit** 한다. 고르는 동작이 아니라 «고른 뒤» 를 만드는 것이다.
  await w.findAllComponents(DxSelectBox)[0].vm.$emit('update:value', 'c1');
  await flushPromises();
}

const save = async (w) => {
  await w.findAll('button').find((b) => b.text().includes('저장') || b.text().includes('등록')).trigger('click');
  await flushPromises();
};

beforeEach(() => { push.mockReset(); });
afterEach(() => { if (w) w.unmount(); w = null; });

describe('ProductFormView — 정가 칸의 «비움» (조건부 잔여 · 08-13 §13-1-1)', () => {
  it('폼이 뜨고 정가 칸이 있다 (기준선)', async () => {
    const w = await open();
    expect(inputFor(w, '정가(원, 선택)').exists()).toBe(true);
    expect(w.text()).toContain('비우거나');
  });

  it('🔴 **재현 시도** — 정가에 값을 넣었다가 지우면 칸에 무엇이 남나', async () => {
    const w = await open();
    const listPrice = inputFor(w, '정가(원, 선택)');

    await type(listPrice, '50000');
    expect(listPrice.element.value).not.toBe('');   // 넣은 것은 들어간다

    await type(listPrice, '');                       // 🔴 지운다

    // 🔴 **재현됐다 (2026-09-21)** — 빈 입력이 「0」으로 되돌아온다. 08-13 §13-1-1 이
    //    «원인 미상» 으로 남긴 그 동작이고, **브라우저 없이 여기서 잡혔다.**
    //    ⚠ 이 단언은 «옳은 동작» 이 아니라 **«우회가 필요한 이유»** 를 못 박는 것이다.
    //    🔴 **이 줄이 빨개지면 DevExtreme 이 고쳐진 것**이고, 그때는 `onSave` 의 `0 → null`
    //       우회를 걷어낼 수 있다. 그래서 «0 을 기대» 하는 형태로 둔다.
    expect(listPrice.element.value).toBe('0');
  });

  // ── 🔴 원인 격리 (2026-09-21) ──────────────────────────────────────────
  //
  // 08-13 은 *"`:min="0"` 을 떼 보았지만 그대로였다 — 즉 원인은 `min` 이 아니다"* 까지 갔고
  // 거기서 멈췄다. `DxNumberBox` 를 **맨몸으로** 띄워 조합을 갈라 보니 범인이 나왔다:
  //
  //   min=0 + format → 지운 뒤 「0」      format 만 → 지운 뒤 「0」
  //   min=0 만       → 안 그렇다          아무것도 없음 → 안 그렇다
  //
  // 🔴 **`format` 이다.** 서식이 걸리면 마스크 입력기가 되고, **빈 입력이 0 으로 확정된다.**
  // ⚠ 그래서 `min` 을 떼도 그대로였던 것이다 — 08-13 의 «min 이 아니다» 는 맞았고, 그 다음이 없었다.
  // ⚠ **고치려면 `format` 을 빼야 하는데** 그러면 천단위 구분이 사라진다 —
  //    즉 08-13 의 «뜻을 넓혀 우회» 는 **원인을 알았어도 같은 선택**이었을 것이다.

  it('🔴 범인은 `format` 이다 — `min` 이 아니다 (08-13 이 멈춘 자리)', async () => {
    const clear = async (props) => {
      const box = mount(DxNumberBox, { props: { value: null, ...props } });
      const el = box.find('input.dx-texteditor-input');
      await el.setValue('50000'); await el.trigger('change');
      await el.setValue(''); await el.trigger('change');
      const left = el.element.value;
      box.unmount();
      return left;
    };

    expect(await clear({ format: '#,##0' })).toBe('0');          // 🔴 서식만으로도 그렇다
    expect(await clear({ min: 0, format: '#,##0' })).toBe('0');  //    min 은 거들 뿐
    expect(await clear({ min: 0 })).not.toBe('0');               // ⚠ min 만으로는 안 그렇다
  });

  it('⚠ 같은 함정이 **재고 칸에도** 있다 — 다만 거기선 0 이 «유효한 값» 이라 더 조용하다', async () => {
    const w = await open();
    const stock = inputFor(w, '재고');

    await type(stock, '10');
    await type(stock, '');

    // 🔴 정가는 «0 = 없음» 으로 읽어 우회했지만, **재고 0 은 품절이라는 뜻**이라 그렇게 못 읽는다.
    //    비우려던 사람이 **조용히 품절을 만든다** — BACKLOG 후보로 적어 둔다(2026-09-21).
    expect(stock.element.value).toBe('0');
  });

  it('⚠ 0 을 넣고 저장하면 서버엔 `listPrice: null` 로 간다 — 우회(0 = 없음)가 도는가', async () => {
    const w = await open();
    await fillRequired(w);
    await type(inputFor(w, '정가(원, 선택)'), '0');

    await save(w);

    expect(createProduct).toHaveBeenCalled();
    expect(createProduct.mock.calls[0][0].listPrice).toBeNull();
  });

  it('정가를 안 건드리면 `listPrice: null` 로 간다', async () => {
    const w = await open();
    await fillRequired(w);

    await save(w);

    expect(createProduct.mock.calls[0][0].listPrice).toBeNull();
  });

  it('🔴 정가가 판매가보다 작거나 같으면 막는다 — 그리고 «비워 두라»고 말해 준다', async () => {
    const w = await open();
    await fillRequired(w);
    await type(inputFor(w, '정가(원, 선택)'), '9000');   // 판매가 10,000 보다 작다

    await save(w);

    expect(createProduct).not.toHaveBeenCalled();
    // ⚠ 막기만 하고 «어떻게 빠져나가나» 를 안 말하면 08-13 사고가 그대로 재현된다.
    expect(w.text()).toContain('정가는 판매가보다 커야 합니다');
    expect(w.text()).toContain('비워');
  });
});
