import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 여섯 번째 뷰 테스트 (2026-08-19, G-5). 패턴은 `ProductTrashAdminView.test.js` 에서 가져왔다.
//
// 🔴 **이 화면을 고른 이유는 여기가 금액을 만드는 자리이기 때문이다.** 되돌리기 어려운 조작은 아니지만
//    (세일은 지우면 원가로 돌아간다), **틀리면 돈이 틀린다** — 그리고 그 틀림은 화면이 멀쩡히 도는 채로
//    일어난다. 특히 아래 둘이 그렇다:
//    ① **`startsAt`(배타 경계)을 폼에 실으면 종료일이 하루 뒤로 보이고**, 그대로 저장하면
//       수정할 때마다 세일이 하루씩 길어진다. 아무 에러도 안 난다.
//    ② **되읽기 문장의 반올림이 서버와 다르면** 관리자는 저장한 뒤에야 1원 차이를 안다.
//
// ⚠ **아래 반올림 단언이 「정수 연산 vs Math.round」를 가르지는 못한다** — JS `Math.round` 도
//    0.5 를 올리므로 두 구현이 이 값들에서 같은 답을 낸다. 여기서 고정하는 것은 그 구별이 아니라
//    **서버가 내는 값과 화면이 내는 값이 같다**는 것이다(서버 쪽 경계는 `ProductDiscountTest` 가
//    같은 숫자로 잡고 있다). 정수 연산을 고른 이유는 Oracle NUMBER 와 double 이 **어디서 갈리는지
//    알 수 없기 때문**이지, 이 테스트가 그 차이를 밟아서가 아니다 — 안 밟은 것을 밟았다고 적지 않는다.

const getProduct = vi.fn();
const fetchProductDiscounts = vi.fn();
const createProductDiscount = vi.fn();
const updateProductDiscount = vi.fn();
const deleteProductDiscount = vi.fn();

vi.mock('../api/product', () => ({
  getProduct: (...a) => getProduct(...a),
  fetchProductDiscounts: (...a) => fetchProductDiscounts(...a),
  createProductDiscount: (...a) => createProductDiscount(...a),
  updateProductDiscount: (...a) => updateProductDiscount(...a),
  deleteProductDiscount: (...a) => deleteProductDiscount(...a),
  // ⚠ 실제 구현과 같은 모양으로 둔다 — 되읽기 문장의 단언이 이 형식을 읽는다.
  priceText: (v) => (v != null ? Number(v).toLocaleString('ko-KR') + '원' : ''),
  discountStatusText: (s) => ({ UPCOMING: '예정', ACTIVE: '진행 중', ENDED: '종료' }[s] || s),
}));

import ProductDiscountAdminView from './ProductDiscountAdminView.vue';

const PRODUCT_ID = '019f7d1c-e0b4-7000-8000-000000000001';

function product(overrides = {}) {
  return {
    id: PRODUCT_ID, name: 'ZZ-세일상품',
    price: 10000, regularPrice: 10000, discountRate: null, discountEndsAt: null,
    ...overrides,
  };
}

/**
 * 서버가 주는 할인 한 줄.
 * ⚠ **`startDate`·`endDate`(포함)와 `startsAt`·`endsAt`(배타 경계)이 둘 다 온다** — 화면이
 *    어느 쪽을 쓰는지가 이 테스트의 요점이라 **일부러 서로 다른 날**로 둔다.
 */
function discountRow(overrides = {}) {
  return {
    id: 'd1', rate: 20,
    startDate: '2026-08-22', endDate: '2026-08-24',
    startsAt: '2026-08-21T15:00:00Z', endsAt: '2026-08-24T15:00:00Z', // = 8/25 00:00 KST
    status: 'ACTIVE',
    ...overrides,
  };
}

describe('ProductDiscountAdminView', () => {
  beforeEach(() => {
    vi.stubGlobal('confirm', vi.fn(() => true));
    getProduct.mockReset().mockResolvedValue(product());
    fetchProductDiscounts.mockReset().mockResolvedValue([]);
    createProductDiscount.mockReset().mockResolvedValue('new-id');
    updateProductDiscount.mockReset().mockResolvedValue(undefined);
    deleteProductDiscount.mockReset().mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  async function mountView() {
    const w = mount(ProductDiscountAdminView, { props: { id: PRODUCT_ID } });
    await flushPromises();
    return w;
  }

  /** 폼 칸 셋을 채운다. DOM 을 통해 채워야 v-model 이 실제로 도는 것을 본다. */
  async function fill(w, { rate, start, end }) {
    const inputs = w.findAll('form input');
    if (rate !== undefined) await inputs[0].setValue(rate);
    if (start !== undefined) await inputs[1].setValue(start);
    if (end !== undefined) await inputs[2].setValue(end);
  }

  // ── 폼이 만들 것을 되읽어 준다 ─────────────────────────────

  it('🔴 되읽기 문장이 **금액까지** 말한다 — 그게 결과다', async () => {
    const w = await mountView();
    await fill(w, { rate: 20, start: '2026-08-22', end: '2026-08-24' });

    expect(w.text()).toContain('8월 22일부터 8월 24일까지 20%');
    expect(w.text()).toContain('10,000원이 8,000원이 됩니다');
  });

  it('🔴 반올림이 서버와 같다 — 12,345원에 13%는 10,740원이다', async () => {
    getProduct.mockResolvedValue(product({ price: 12345, regularPrice: 12345 }));
    const w = await mountView();
    await fill(w, { rate: 13, start: '2026-08-22', end: '2026-08-24' });

    // 12345 × 87 / 100 = 10740.15 → 10740 (내림)
    expect(w.text()).toContain('10,740원이 됩니다');
  });

  it('🔴 **정확히 0.5는 올린다** — 서버(`ProductDiscountTest`)와 같은 값을 낸다', async () => {
    getProduct.mockResolvedValue(product({ price: 12345, regularPrice: 12345 }));
    const w = await mountView();
    await fill(w, { rate: 10, start: '2026-08-22', end: '2026-08-24' });

    // 12345 × 90 / 100 = 11110.5 → **11111**
    expect(w.text()).toContain('11,111원이 됩니다');
  });

  it('세 칸이 다 차기 전에는 되읽지 않는다 — 반쪽 문장이 더 헷갈린다', async () => {
    const w = await mountView();
    await fill(w, { rate: 20 });
    expect(w.text()).not.toContain('됩니다');
  });

  // ── 🔴 수정: 어느 날짜를 폼에 싣나 ─────────────────────────

  it('🔴 수정하면 **`startDate`·`endDate`** 가 폼에 들어간다 (배타 경계 `endsAt` 이 아니다)', async () => {
    fetchProductDiscounts.mockResolvedValue([discountRow()]);
    const w = await mountView();

    await w.findAll('button').find((b) => b.text() === '수정').trigger('click');
    await flushPromises();

    const inputs = w.findAll('form input');
    expect(inputs[1].element.value).toBe('2026-08-22');
    // ⚠ `endsAt`(8/25 00:00 KST)을 잘라 썼다면 여기가 '2026-08-25' 가 된다 —
    //    저장할 때마다 세일이 하루씩 길어지는 그 자리다.
    expect(inputs[2].element.value).toBe('2026-08-24');
  });

  it('수정 저장은 그 할인 id 로 간다 — 새 할인을 만들지 않는다', async () => {
    fetchProductDiscounts.mockResolvedValue([discountRow()]);
    const w = await mountView();

    await w.findAll('button').find((b) => b.text() === '수정').trigger('click');
    await flushPromises();
    await fill(w, { rate: 50 });
    await w.find('form').trigger('submit');
    await flushPromises();

    expect(createProductDiscount).not.toHaveBeenCalled();
    expect(updateProductDiscount).toHaveBeenCalledWith(PRODUCT_ID, 'd1', {
      rate: 50, startDate: '2026-08-22', endDate: '2026-08-24',
    });
  });

  // ── 등록 ──────────────────────────────────────────────────

  it('등록은 날짜만 보낸다 — 경계(시각)는 서버가 만든다', async () => {
    const w = await mountView();
    await fill(w, { rate: 20, start: '2026-08-22', end: '2026-08-24' });
    await w.find('form').trigger('submit');
    await flushPromises();

    expect(createProductDiscount).toHaveBeenCalledWith(PRODUCT_ID, {
      rate: 20, startDate: '2026-08-22', endDate: '2026-08-24',
    });
  });

  it('겹침 거절(400)은 **서버 문구 그대로** 보여준다 — 화면이 다시 쓰면 규칙이 바뀔 때 화면만 낡는다', async () => {
    createProductDiscount.mockRejectedValue(new Error('기간이 겹치는 할인이 이미 있습니다.'));
    const w = await mountView();
    await fill(w, { rate: 20, start: '2026-08-22', end: '2026-08-24' });
    await w.find('form').trigger('submit');
    await flushPromises();

    expect(w.text()).toContain('기간이 겹치는 할인이 이미 있습니다.');
  });

  it('종료일이 시작일보다 앞이면 **보내지도 않는다** — 왕복할 이유가 없다', async () => {
    const w = await mountView();
    await fill(w, { rate: 20, start: '2026-08-24', end: '2026-08-22' });
    await w.find('form').trigger('submit');
    await flushPromises();

    expect(createProductDiscount).not.toHaveBeenCalled();
    expect(w.text()).toContain('종료일이 시작일보다 앞입니다');
  });

  // ── 삭제 ──────────────────────────────────────────────────

  it('🔴 **진행 중인** 세일을 지울 때는 확인 문구가 그 사실을 말한다', async () => {
    fetchProductDiscounts.mockResolvedValue([discountRow({ status: 'ACTIVE' })]);
    const w = await mountView();

    await w.findAll('button').find((b) => b.text() === '삭제').trigger('click');
    await flushPromises();

    expect(window.confirm).toHaveBeenCalled();
    expect(window.confirm.mock.calls[0][0]).toContain('지금 진행 중인 세일입니다');
  });

  it('예정 세일 삭제에는 그 경고가 없다 — 아무 데도 안 걸린 세일이다', async () => {
    fetchProductDiscounts.mockResolvedValue([discountRow({ status: 'UPCOMING' })]);
    const w = await mountView();

    await w.findAll('button').find((b) => b.text() === '삭제').trigger('click');
    await flushPromises();

    expect(window.confirm.mock.calls[0][0]).not.toContain('지금 진행 중인 세일입니다');
  });

  // ── 목록 · 요약 ───────────────────────────────────────────

  it('지난 것·진행 중·예정을 **모두** 보여준다 — 다음 세일을 언제 걸지 알려면 필요하다', async () => {
    fetchProductDiscounts.mockResolvedValue([
      discountRow({ id: 'd0', status: 'ENDED', rate: 10 }),
      discountRow({ id: 'd1', status: 'ACTIVE', rate: 20 }),
      discountRow({ id: 'd2', status: 'UPCOMING', rate: 30 }),
    ]);
    const w = await mountView();

    expect(w.findAll('li')).toHaveLength(3);
    expect(w.text()).toContain('종료');
    expect(w.text()).toContain('진행 중');
    expect(w.text()).toContain('예정');
  });

  it('세일 중이면 요약이 **세일 전 가격과 지금 가격을 나란히** 말한다', async () => {
    getProduct.mockResolvedValue(product({ price: 8000, regularPrice: 10000, discountRate: 20 }));
    fetchProductDiscounts.mockResolvedValue([discountRow()]);
    const w = await mountView();

    expect(w.text()).toContain('10,000원');
    expect(w.text()).toContain('8,000원');
    expect(w.text()).toContain('20% 세일 중');
  });

  it('불러오기 실패를 **빈 목록으로 위장하지 않는다** — 0건이면 「세일이 없다」로 읽힌다', async () => {
    fetchProductDiscounts.mockRejectedValue(new Error('서버 오류'));
    const w = await mountView();

    expect(w.text()).toContain('불러오지 못했습니다');
    expect(w.text()).toContain('서버 오류');
  });
});
