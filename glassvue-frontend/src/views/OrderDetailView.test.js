import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 여덟 번째 뷰 테스트 (2026-08-24, BACKLOG G-4) — **부분 취소만** 본다.
//
// 🔴 **이 파일이 있는 진짜 이유는 「같은 식이 두 곳에 있다」는 것이다.** 환불 예정 금액을 누르기
//    **전에** 보여주려고 화면이 서버와 같은 배분식을 갖고 있다(`itemCancelPreview`).
//    CLAUDE.md 가 경계하는 모양이라 — 한쪽만 고쳐지면 어긋난다 — **어긋나면 잡히게** 해 둔다:
//    🔴 **아래 숫자는 서버 테스트가 단언하는 값과 글자 그대로 같다**
//    (`OrderPartialCancelTest` · `OrderPartialCancelIntegrationTest`: 12,001 · 2,142 · 857).
//    한쪽 식이 바뀌면 여기와 저기 중 한쪽이 빨개진다.
//    (`ProductDiscountAdminView` 가 반올림을 같은 방식으로 묶어 둔 것과 같은 장치다.)
//
// ⚠ **화면 전체를 덮지 않는다** — 결제·발송·반품은 여기서 안 본다. 새로 만든 자리만 잡는다.

const push = vi.fn();
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: 'o1' } }),
  useRouter: () => ({ push: (...a) => push(...a) }),
}));

const getOrder = vi.fn();
const cancelOrderItem = vi.fn();
const cancelOrderItemByAdmin = vi.fn();
const requestReturn = vi.fn();
const requestReturnByAdmin = vi.fn();

vi.mock('../api/order', async (importOriginal) => ({
  // ⚠ **`returnApproveConfirm` 은 진짜를 쓴다** — 이 화면이 그 함수를 «실제로 부르는가» 가
  //    검증 대상이다(2026-08-27). 가짜로 갈아끼우면 문구를 다시 인라인으로 적어 놔도 초록이다.
  returnApproveConfirm: (await importOriginal()).returnApproveConfirm,
  getOrder: (...a) => getOrder(...a),
  cancelOrderItem: (...a) => cancelOrderItem(...a),
  cancelOrderItemByAdmin: (...a) => cancelOrderItemByAdmin(...a),
  payOrder: vi.fn(), shipOrder: vi.fn(), deliverOrder: vi.fn(), cancelOrder: vi.fn(),
  requestReturn: (...a) => requestReturn(...a),
  requestReturnByAdmin: (...a) => requestReturnByAdmin(...a),
  approveReturn: vi.fn(), rejectReturn: vi.fn(),
  orderStatusText: (s) => s,
  orderStatusClass: () => '',
  DELIVERY_CARRIERS: [],
}));

import OrderDetailView from './OrderDetailView.vue';
import { authState } from '../stores/auth';

const ME = 'me-1';

/** G-4 검산 표본 — A 20,000 · B 15,000 / 쿠폰 5,000 / 적립금 2,000 / 배송비 무료 → 결제 28,000. */
function order(overrides = {}) {
  return {
    id: 'o1', orderNo: '20260824-0001', memberId: ME, buyerNickname: '구매자',
    status: 'ORDERED',
    totalPrice: 35_000, shippingFee: 0,
    couponName: 'ZZ쿠폰', couponDiscount: 5_000,
    usedPoint: 2_000, earnedPoint: 0,
    payAmount: 28_000,
    cancelledItemsTotal: 0, refundedAmount: 0, cancelledPoint: 0,
    // 반품이 회수해 간 몫(G-10). 취소 쪽 셋과 **짝**이라 픽스처에서도 나란히 둔다.
    returnedItemsTotal: 0, returnedCouponDiscount: 0, returnedPoint: 0, refundAmount: 0,
    reversedEarnedPoint: 0,
    // 반품 기한(§I-9, 2026-08-27). ⚠ **둘을 나란히 둔다** — `returnDeadline` 은 «언제까지인가»(문구용),
    //    `returnRequestable` 은 «지금 되나»(서버의 판정). 화면이 앞의 것으로 판정하면 서버와 두 벌이 된다.
    returnDeadline: '2026-08-31T00:00:00Z', returnRequestable: true,
    items: [
      item({ orderItemId: 'i-a', productName: 'ZZ-A', price: 20_000, lineTotal: 20_000 }),
      item({ orderItemId: 'i-b', productName: 'ZZ-B', price: 15_000, lineTotal: 15_000 }),
    ],
    createdAt: '2026-08-24T00:00:00Z',
    ...overrides,
  };
}

function item(o = {}) {
  const merged = {
    productId: 'p1', variantId: 'v1', orderItemId: 'i-x', productName: 'ZZ상품',
    optionName: null, productImageUrl: null,
    price: 10_000, regularPrice: null, listPrice: null,
    quantity: 1, lineTotal: 10_000,
    cancelledQuantity: 0, remainingQuantity: 1,
    returnedQuantity: 0, returnRequestedQuantity: 0,
    ...o,
  };
  // ⚠ `returnableQuantity` 는 **서버가 계산해 보내는** 값이다(G-10). 픽스처도 서버가 낼 값을
  //    그대로 흉내 낸다 — 손으로 따로 적으면 화면이 «서버가 안 주는 값» 에 기대게 된다.
  return { returnableQuantity: merged.remainingQuantity - merged.returnRequestedQuantity, ...merged };
}

describe('OrderDetailView — 부분 취소 (G-4)', () => {
  let w;

  beforeEach(() => {
    authState.user = { id: ME, role: 'USER' };
    getOrder.mockReset().mockResolvedValue(order());
    cancelOrderItem.mockReset().mockResolvedValue(undefined);
    cancelOrderItemByAdmin.mockReset().mockResolvedValue(undefined);
    push.mockReset();
  });

  afterEach(() => {
    if (w) w.unmount();
    w = null;
    authState.user = null;
  });

  async function open(data) {
    if (data) getOrder.mockResolvedValue(data);
    w = mount(OrderDetailView, {
      props: { id: 'o1' },
      global: { stubs: { RouterLink: true, ItemThumb: true } },
    });
    await flushPromises();
    return w;
  }

  /** 「이 품목 취소」 버튼들. 품목 순서와 같다. */
  const cancelButtons = (w) => w.findAll('button').filter((b) => b.text() === '이 품목 취소');

  async function openFormFor(w, index) {
    await cancelButtons(w)[index].trigger('click');
    await flushPromises();
    return w.find('input[type="number"]');
  }

  // ── 🔴 배분 미리보기 — 서버와 같은 숫자여야 한다 ──────────────

  it('🔴 B(15,000) 취소 미리보기가 **12,001원** 이다 — 서버 테스트와 같은 숫자다', async () => {
    const w = await open();
    await openFormFor(w, 1);

    expect(w.text()).toContain('12,001원');
    expect(w.text()).toContain('을 환불해요');
  });

  it('🔴 쿠폰 몫 **2,142원** 이 빠진다고 말한다 — 이게 이 화면에서 가장 놀랄 숫자다', async () => {
    const w = await open();
    await openFormFor(w, 1);

    expect(w.text()).toContain('2,142원');
    expect(w.text()).toContain('쿠폰은 그대로 남은 주문에 걸려 있어요');
  });

  it('🔴 적립금 몫 **857원** 은 계정으로 돌아간다고 갈라 말한다 — 환불액과 섞으면 두 번 받은 걸로 읽힌다', async () => {
    const w = await open();
    await openFormFor(w, 1);

    expect(w.text()).toContain('857원');
    expect(w.text()).toContain('계정으로 돌아가요');
  });

  it('🔴 두 번째 취소는 **남은 값** 기준으로 센다 — 원본으로 세면 서버와 갈린다', async () => {
    // B 를 이미 뺀 상태. 남은 상품합계 20,000 · 남은 쿠폰 2,858 · 남은 적립금 1,143.
    const w = await open(order({
      cancelledItemsTotal: 15_000, refundedAmount: 12_001, cancelledPoint: 857,
      payAmount: 15_999,
      items: [
        item({ orderItemId: 'i-a', productName: 'ZZ-A', price: 20_000, lineTotal: 20_000 }),
        item({ orderItemId: 'i-b', productName: 'ZZ-B', price: 15_000, lineTotal: 15_000,
               cancelledQuantity: 1, remainingQuantity: 0 }),
      ],
    }));
    await openFormFor(w, 0); // 남은 것은 A 뿐이라 버튼도 하나다

    // 분모·분자가 «남은 값» 이면 A 의 몫은 남은 전부다 → 환불 20,000 − 2,858 − 1,143 = 15,999
    expect(w.text()).toContain('15,999원');
    // ⚠ 원본(35,000·5,000·2,000)으로 셌다면 20000 − 2857 − 1142 = 16,001 이 나온다.
    expect(w.text()).not.toContain('16,001원');
  });

  it('수량을 올리면 환불액도 따라 오른다', async () => {
    const w = await open(order({
      totalPrice: 30_000, couponDiscount: 0, usedPoint: 0, payAmount: 30_000,
      items: [item({ orderItemId: 'i-a', price: 10_000, lineTotal: 30_000, quantity: 3, remainingQuantity: 3 })],
    }));
    const input = await openFormFor(w, 0);

    expect(w.text()).toContain('10,000원');
    await input.setValue(2);
    expect(w.text()).toContain('20,000원');
  });

  it('범위를 벗어난 수량이면 **문장을 아예 안 그린다** — 반쪽 문장이 더 헷갈린다', async () => {
    const w = await open();
    const input = await openFormFor(w, 1);

    await input.setValue(2); // 남은 수량은 1
    expect(w.text()).not.toContain('을 환불해요');
    expect(w.findAll('button').find((b) => b.text() === '취소하기').attributes('disabled')).toBeDefined();
  });

  // ── 🔴 마지막 품목 경고 ────────────────────────────────────────

  it('🔴 마지막 품목이면 **주문 전체가 취소된다**고 미리 말한다', async () => {
    const w = await open(order({
      cancelledItemsTotal: 15_000, refundedAmount: 12_001, cancelledPoint: 857,
      items: [
        item({ orderItemId: 'i-a', productName: 'ZZ-A', price: 20_000, lineTotal: 20_000 }),
        item({ orderItemId: 'i-b', productName: 'ZZ-B', price: 15_000, lineTotal: 15_000,
               cancelledQuantity: 1, remainingQuantity: 0 }),
      ],
    }));
    await openFormFor(w, 0);

    expect(w.text()).toContain('주문 전체가 취소');
    expect(w.text()).toContain('쿠폰도 돌아와요');
  });

  it('아직 다른 품목이 남아 있으면 그 경고를 안 띄운다 — 매번 뜨면 아무도 안 읽는다', async () => {
    const w = await open();
    await openFormFor(w, 1);
    expect(w.text()).not.toContain('주문 전체가 취소');
  });

  // ── 취소된 흔적 ───────────────────────────────────────────────

  it('🔴 원본 수량과 취소 수량을 **둘 다** 보여준다 — 「3개 중 1개 취소됨」', async () => {
    const w = await open(order({
      items: [item({ orderItemId: 'i-a', productName: 'ZZ-A', price: 10_000, lineTotal: 30_000,
                     quantity: 3, cancelledQuantity: 1, remainingQuantity: 2 })],
    }));

    expect(w.text()).toContain('3개 중');
    expect(w.text()).toContain('1개 취소됨');
    expect(w.text()).toContain('20,000원'); // 지금 살아 있는 금액
  });

  it('전량 취소된 품목은 「전량 취소됨」이라 말하고 취소 버튼이 사라진다', async () => {
    const w = await open(order({
      items: [item({ orderItemId: 'i-a', quantity: 1, cancelledQuantity: 1, remainingQuantity: 0 })],
    }));

    expect(w.text()).toContain('전량 취소됨');
    expect(cancelButtons(w)).toHaveLength(0);
  });

  it('🔴 합계 카드가 **원본과 빠진 것을 갈라** 보여주고, 총액 이름이 바뀐다', async () => {
    const w = await open(order({
      cancelledItemsTotal: 15_000, refundedAmount: 12_001, cancelledPoint: 857, payAmount: 15_999,
    }));

    expect(w.text()).toContain('35,000원');  // 원본 스냅샷
    expect(w.text()).toContain('환불액');
    expect(w.text()).toContain('12,001원');
    expect(w.text()).toContain('돌려받은 적립금');
    // 「결제 금액」이라 부르면 처음 낸 금액으로 읽힌다.
    expect(w.text()).toContain('남은 결제 금액');
  });

  it('부분 취소가 없으면 그 줄들이 아예 안 나온다 — 예전 화면과 똑같이 읽힌다', async () => {
    const w = await open();
    expect(w.text()).not.toContain('환불액');
    expect(w.text()).not.toContain('남은 결제 금액');
    expect(w.text()).toContain('결제 금액');
  });

  it('🔴 전량이 빠진 주문은 「남은 결제 금액 0원」이고 환불액이 **배송비까지** 포함한다', async () => {
    // 실측(`20260824-5297`)에서 여기가 「남은 결제 금액 3,000원」으로 나왔었다 — 취소된 주문에
    // 배송비만 덩그러니 남은 모양이다. 서버가 0 을 주고 환불액에 배송비를 넣는다.
    const w = await open(order({
      status: 'CANCELLED',
      totalPrice: 25_000, shippingFee: 3_000, couponDiscount: 5_000, usedPoint: 2_000,
      cancelledItemsTotal: 25_000, refundedAmount: 21_000, cancelledPoint: 2_000,
      payAmount: 0,
      items: [
        item({ orderItemId: 'i-a', productName: 'ZZ-A', price: 15_000, lineTotal: 15_000,
               cancelledQuantity: 1, remainingQuantity: 0 }),
        item({ orderItemId: 'i-b', productName: 'ZZ-B', price: 10_000, lineTotal: 10_000,
               cancelledQuantity: 1, remainingQuantity: 0 }),
      ],
    }));

    expect(w.text()).toContain('남은 결제 금액');
    expect(w.text()).toContain('0원');
    expect(w.text()).toContain('21,000원'); // 처음 결제한 금액이 전부 돌아왔다
    expect(cancelButtons(w)).toHaveLength(0); // 뺄 것이 없다
  });

  // ── 🔴 어느 경로로 보내나 (WA §2-3 — 소유 기준) ─────────────────

  it('🔴 본인 주문이면 **본인 경로**로 보낸다 — 관리자여도 그렇다', async () => {
    authState.user = { id: ME, role: 'ADMIN' };
    const w = await open();
    await openFormFor(w, 1);
    await w.findAll('button').find((b) => b.text() === '취소하기').trigger('click');
    await flushPromises();

    expect(cancelOrderItem).toHaveBeenCalledWith('o1', 'i-b', 1);
    expect(cancelOrderItemByAdmin).not.toHaveBeenCalled();
  });

  it('🔴 관리자가 **남의** 주문을 뺄 때만 관리자 경로다 — 안 그러면 원장에 안 남는다', async () => {
    authState.user = { id: 'admin-9', role: 'ADMIN' };
    const w = await open();
    await openFormFor(w, 1);
    await w.findAll('button').find((b) => b.text() === '취소하기').trigger('click');
    await flushPromises();

    expect(cancelOrderItemByAdmin).toHaveBeenCalledWith('o1', 'i-b', 1);
    expect(cancelOrderItem).not.toHaveBeenCalled();
  });

  it('취소 뒤 주문을 다시 읽는다 — 화면이 옛 금액을 들고 있으면 다음 취소가 서버와 갈린다', async () => {
    const w = await open();
    await openFormFor(w, 1);
    expect(getOrder).toHaveBeenCalledTimes(1);

    await w.findAll('button').find((b) => b.text() === '취소하기').trigger('click');
    await flushPromises();

    expect(getOrder).toHaveBeenCalledTimes(2);
  });

  it('서버가 거절하면 그 문구를 그대로 보여준다', async () => {
    cancelOrderItem.mockRejectedValue(new Error('취소 수량이 남은 수량을 벗어났습니다.'));
    const w = await open();
    await openFormFor(w, 1);
    await w.findAll('button').find((b) => b.text() === '취소하기').trigger('click');
    await flushPromises();

    expect(w.text()).toContain('취소 수량이 남은 수량을 벗어났습니다.');
  });

  // ── 언제 버튼이 보이나 ────────────────────────────────────────

  it('발송된 주문에는 품목 취소 버튼이 없다 — 그 자리는 반품이 맡는다', async () => {
    const w = await open(order({ status: 'SHIPPED' }));
    expect(cancelButtons(w)).toHaveLength(0);
  });

  it('남의 주문을 보는 일반 사용자에게는 안 보인다', async () => {
    authState.user = { id: 'someone-else', role: 'USER' };
    const w = await open();
    expect(cancelButtons(w)).toHaveLength(0);
  });
});

/**
 * 아홉 번째 뷰 테스트 (2026-08-25, BACKLOG G-10) — **부분 반품만** 본다.
 *
 * 🔴 **여기 있는 이유도 위와 같다: 같은 배분식이 서버와 화면 두 곳에 있다**(`returnPreview`).
 *    🔴 **아래 숫자는 서버 테스트가 단언하는 값과 글자 그대로 같다**
 *    (`OrderPartialReturnTest`: 12,858 · 17,143 · 30,000 · 회수 쿠폰 2,142/2,857).
 *    한쪽 식이 바뀌면 여기와 저기 중 한쪽이 빨개진다.
 *
 * ⚠ **취소와 갈리는 지점이 숫자에 있다** — 같은 B 를 빼도 취소는 12,001, 반품은 12,858 이다.
 *    차이 857 이 «적립금 몫» 이고 반품은 그것을 환불액 **안에** 담아 돌려준다.
 *    두 파일이 같은 표본을 쓰는 이유가 그 대비를 보이기 위해서다.
 */
describe('OrderDetailView — 부분 반품 (G-10)', () => {
  let w;

  /** 배송완료 주문 — 반품은 여기서만 시작된다. */
  const deliveredOrder = (overrides = {}) => order({ status: 'DELIVERED', ...overrides });

  beforeEach(() => {
    authState.user = { id: ME, role: 'USER' };
    getOrder.mockReset().mockResolvedValue(deliveredOrder());
    requestReturn.mockReset().mockResolvedValue(undefined);
    push.mockReset();
  });

  afterEach(() => {
    if (w) w.unmount();
    w = null;
    authState.user = null;
  });

  async function openReturnForm(data) {
    if (data) getOrder.mockResolvedValue(data);
    w = mount(OrderDetailView, {
      props: { id: 'o1' },
      global: { stubs: { RouterLink: true, ItemThumb: true } },
    });
    await flushPromises();
    await w.findAll('button').find((b) => b.text() === '반품 요청').trigger('click');
    await flushPromises();
    return w;
  }

  /** 폼 안의 수량 입력들 — 반품 가능한 품목 순서와 같다. */
  const qtyInputs = (w) => w.findAll('input[type="number"]');

  it('🔴 기본값은 «남은 것 전부» 이고 환불 예정이 **30,000원** 이다 — 서버의 refundableAmount 와 같다', async () => {
    const w = await openReturnForm();

    expect(qtyInputs(w).map((i) => i.element.value)).toEqual(['1', '1']);
    expect(w.text()).toContain('30,000원');
    // 🔴 전량이 빠지므로 쿠폰이 돌아온다 — 되돌리기 어려운 조작을 조용히 하지 않는다.
    expect(w.text()).toContain('사용한 쿠폰이 돌아옵니다');
  });

  it('🔴 B(15,000)만 반품하면 **12,858원** 이다 — 같은 B 를 «취소» 하면 12,001 이다', async () => {
    const w = await openReturnForm();
    await qtyInputs(w)[0].setValue(0);   // A 를 뺀다
    await flushPromises();

    expect(w.text()).toContain('12,858원');
    // 회수되는 쿠폰 몫 5,000 × 15,000 / 35,000 = 2,142.8 → 2,142
    expect(w.text()).toContain('2,142원');
    // ⚠ 남은 것이 있으므로 쿠폰 안내가 안 뜬다.
    expect(w.text()).not.toContain('사용한 쿠폰이 돌아옵니다');
  });

  it('🔴 A(20,000)만 반품하면 **17,143원** 이다 — 회수 쿠폰 몫이 2,857 로 갈린다(경로 의존)', async () => {
    const w = await openReturnForm();
    await qtyInputs(w)[1].setValue(0);
    await flushPromises();

    expect(w.text()).toContain('17,143원');
    expect(w.text()).toContain('2,857원');
  });

  it('요청 본문에 **고른 품목·수량**이 실린다 (사유만 보내던 옛 계약이 아니다)', async () => {
    const w = await openReturnForm();
    await qtyInputs(w)[0].setValue(0);
    await w.find('input:not([type="number"])').setValue('ZZ-단순 변심');
    await w.findAll('button').filter((b) => b.text() === '반품 요청').at(-1).trigger('click');
    await flushPromises();

    expect(requestReturn).toHaveBeenCalledWith('o1', 'ZZ-단순 변심', [{ orderItemId: 'i-b', quantity: 1 }]);
  });

  it('⚠ 하나도 안 고르면 보내지 않는다 — 빈 반품 요청을 만들지 않는다', async () => {
    const w = await openReturnForm();
    await qtyInputs(w)[0].setValue(0);
    await qtyInputs(w)[1].setValue(0);
    await w.find('input:not([type="number"])').setValue('ZZ-사유');
    await w.findAll('button').filter((b) => b.text() === '반품 요청').at(-1).trigger('click');
    await flushPromises();

    expect(requestReturn).not.toHaveBeenCalled();
    expect(w.text()).toContain('반품할 품목을 하나 이상');
  });

  it('🔴 이미 빠진 품목은 폼에 안 나온다 — 취소분·반품분·요청 중인 몫을 다 뺀 값이 상한이다', async () => {
    const w = await openReturnForm(deliveredOrder({
      items: [
        // 3개 중 1개 취소 · 1개 반품 → 남은 1개만 고를 수 있다
        item({ orderItemId: 'i-a', price: 10_000, lineTotal: 30_000, quantity: 3,
               cancelledQuantity: 1, returnedQuantity: 1, remainingQuantity: 1 }),
        // 남은 것이 없다 → 아예 안 나온다
        item({ orderItemId: 'i-b', price: 15_000, lineTotal: 15_000, cancelledQuantity: 1, remainingQuantity: 0 }),
      ],
    }));

    const inputs = qtyInputs(w);
    expect(inputs).toHaveLength(1);
    expect(inputs[0].attributes('max')).toBe('1');
  });

  it('🔴 **이미 반품한 이력이 있는 주문** — 기본값은 남은 2개, 1개만 고르면 8,333원 (서버와 같은 숫자)', async () => {
    // 🔴 **이 테스트는 변형 주입이 찾아낸 구멍이다** (2026-08-25). 다른 픽스처가 전부
    //    `returnedItemsTotal: 0` 이라, 분모에서 «기존 반품분» 을 빼는 줄을 지워도 아무도 안 빨개졌고
    //    기본 수량을 `quantity`(원본)로 바꿔도 마찬가지였다 — 표본에서 둘이 같았기 때문이다.
    //
    // 표본: 지바 10,000 × 3 · 쿠폰 5,000. **1개는 이미 반품됐다**(쿠폰 몫 1,666 회수).
    //       서버 `sameItemAcrossThreeRounds` 의 2회차와 **같은 자리**다.
    const w = await openReturnForm(deliveredOrder({
      totalPrice: 30_000, couponDiscount: 5_000, usedPoint: 0, payAmount: 25_000,
      returnedItemsTotal: 10_000, returnedCouponDiscount: 1_666, returnedPoint: 0,
      items: [item({ orderItemId: 'i-a', price: 10_000, lineTotal: 30_000, quantity: 3,
                     returnedQuantity: 1, remainingQuantity: 2 })],
    }));

    // 기본값은 **남은 2개** — 원본 3개가 아니다(이미 나간 1개를 또 보낼 수 없다).
    expect(qtyInputs(w)[0].element.value).toBe('2');

    await qtyInputs(w)[0].setValue(1);
    await flushPromises();
    // 분모가 20,000(= 30,000 − 이미 반품한 10,000) 이라 쿠폰 몫 3,334×10,000/20,000 = 1,667
    expect(w.text()).toContain('8,333원');
    expect(w.text()).toContain('1,667원');
  });

  it('품목 줄이 «취소»와 «반품»을 갈라 적는다 — 합치면 무엇이 왜 빠졌는지 못 읽는다', async () => {
    const w = await openReturnForm(deliveredOrder({
      items: [item({ orderItemId: 'i-a', price: 10_000, lineTotal: 30_000, quantity: 3,
                     cancelledQuantity: 1, returnedQuantity: 1, remainingQuantity: 1 })],
    }));

    expect(w.text()).toContain('3개 중 1개 취소됨');
    expect(w.text()).toContain('3개 중 1개 반품됨');
  });

  it('⚠ 승인 대기 중인 요청은 «아직 안 빠진 것» 이라 따로 말한다', async () => {
    getOrder.mockResolvedValue(order({
      status: 'RETURN_REQUESTED',
      refundAmount: 12_858,
      items: [
        item({ orderItemId: 'i-a', price: 20_000, lineTotal: 20_000 }),
        item({ orderItemId: 'i-b', price: 15_000, lineTotal: 15_000, returnRequestedQuantity: 1 }),
      ],
    }));
    w = mount(OrderDetailView, {
      props: { id: 'o1' },
      global: { stubs: { RouterLink: true, ItemThumb: true } },
    });
    await flushPromises();

    expect(w.text()).toContain('1개 반품 요청됨 (승인 대기)');
    // 🔴 서버가 «승인하면 얼마인가» 를 계산해 보내므로 관리자가 누르기 전에 금액을 본다.
    expect(w.text()).toContain('환불 예정 적립금');
    expect(w.text()).toContain('12,858원');
  });

  /**
   * 🔴 **사용자가 잡은 자리다**(2026-08-27, §I-7 후속). 관리자 «목록» 의 승인 문구에만 수량을
   * 넣고 이 화면을 안 열어서, 같은 주문을 목록에서 승인하면 「N개 중 M개」, 상세에서 승인하면
   * 수량이 없었다.
   *
   * ⚠ **여기가 지키는 것은 «문구» 가 아니라 «배선» 이다** — 문구 규칙 자체는
   * `api/order.test.js` 가 본다. 이 테스트는 **화면이 그 함수를 실제로 부르는가**를 본다.
   * 문구를 다시 인라인으로 적어 놓으면 여기가 빨개진다.
   */
  it('🔴 승인 confirm 이 «몇 개 중 몇 개» 를 말한다 — 목록과 같은 말이어야 한다', async () => {
    authState.user = { id: 'admin-1', role: 'ADMIN' };
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    // 관리자가 남의 주문을 보는 상황. 2품목(2개 + 1개) 중 1개만 요청됐다.
    getOrder.mockResolvedValue(order({
      status: 'RETURN_REQUESTED', memberId: 'someone-else',
      items: [
        item({ orderItemId: 'i-a', productName: 'ZZ-A', quantity: 2, returnRequestedQuantity: 1 }),
        item({ orderItemId: 'i-b', productName: 'ZZ-B', quantity: 1, returnRequestedQuantity: 0 }),
      ],
    }));
    w = mount(OrderDetailView, {
      props: { id: 'o1' },
      global: { stubs: { RouterLink: true, ItemThumb: true } },
    });
    await flushPromises();

    await w.findAll('button').find((b) => b.text() === '반품 승인').trigger('click');

    expect(confirmSpy.mock.calls[0][0]).toContain('3개 중 1개');
    confirmSpy.mockRestore();
  });
});

/**
 * 열한 번째 묶음 (2026-08-25, BACKLOG §I-3 · §I-4) — **부분 반품된 주문이 «돈 이야기» 를 하는가.**
 *
 * 🔴 **감사가 잡은 자리다.** 부분 반품 승인은 주문을 `DELIVERED` 로 되돌리는데, 화면은 전량 시절
 *    조건(`RETURN_REQUESTED || RETURNED || returnRejectedAt`)만 봐서 **반품 카드가 통째로 사라졌다.**
 *    합계 카드에도 반품 섹션이 없어 **위 줄들의 산수가 안 맞는데 차액이 어디에도 없었고**,
 *    적립 문구는 회수분을 모른 채 원본을 말했다.
 * ⚠ 🔴 **셋 다 서버는 값을 보내고 있었다** — 화면만 안 읽었다. WA §1-2-1 «남기는 쪽 ↔ 보는 쪽».
 */
describe('OrderDetailView — 부분 반품된 주문의 표시 (§I-3 · §I-4)', () => {
  let w;

  /** 배송완료로 되돌아온, 일부만 반품된 주문. 지바 2개 중 1개(10,000)를 반품했다. */
  const partiallyReturned = (overrides = {}) => order({
    status: 'DELIVERED',
    totalPrice: 35_000, couponDiscount: 5_000, usedPoint: 2_000, earnedPoint: 500,
    returnedItemsTotal: 10_000, returnedCouponDiscount: 1_428, returnedPoint: 571,
    reversedEarnedPoint: 200,
    returnReason: 'ZZ-단순 변심',
    returnedAt: '2026-08-25T02:00:00Z',
    payAmount: 18_001,
    ...overrides,
  });

  beforeEach(() => { authState.user = { id: ME, role: 'USER' }; push.mockReset(); });
  afterEach(() => { if (w) w.unmount(); w = null; authState.user = null; });

  async function open(data) {
    getOrder.mockReset().mockResolvedValue(data);
    w = mount(OrderDetailView, {
      props: { id: 'o1' },
      global: { stubs: { RouterLink: true, ItemThumb: true } },
    });
    await flushPromises();
    return w;
  }

  it('🔴 반품 카드가 **사라지지 않는다** — 승인 뒤 주문은 DELIVERED 로 되돌아온다', async () => {
    const w = await open(partiallyReturned());

    expect(w.text()).toContain('일부 반품 완료 (남은 품목은 그대로)');
    // 요청 사유가 화면에 남아야 «무엇 때문에 돌려보냈나» 를 고객이 되짚을 수 있다.
    expect(w.text()).toContain('ZZ-단순 변심');
  });

  it('🔴 합계 카드가 **반품으로 빠진 것**을 갈라 보여준다 — 취소 섹션과 줄이 다르다', async () => {
    const w = await open(partiallyReturned());

    expect(w.text()).toContain('반품된 품목');
    // 환불 적립금 = 반품금액 10,000 − 회수 쿠폰 1,428 = 8,572
    expect(w.text()).toContain('8,572원');
    expect(w.text()).toContain('회수된 적립');
    // ⚠ **«돌려받은 적립금» 줄은 없어야 한다** — 반품 환불액에 쓴 적립금이 이미 들어 있어서,
    //    취소처럼 따로 적으면 두 번 받은 것처럼 읽힌다.
    expect(w.text()).not.toContain('돌려받은 적립금');
  });

  it('총액 라벨이 «남은 결제 금액» 이다 — 처음 낸 금액으로 읽히면 안 된다', async () => {
    const w = await open(partiallyReturned());
    expect(w.text()).toContain('남은 결제 금액');
    expect(w.text()).not.toContain('결제 금액\n');
  });

  it('🔴 적립 문구가 **회수분을 뺀다** — 500 준 뒤 200 회수됐으면 300 이다', async () => {
    const w = await open(partiallyReturned());

    expect(w.text()).toContain('300원');
    expect(w.text()).toContain('반품으로 200원 회수');
    // 🔴 원본을 그대로 말하던 자리다.
    expect(w.text()).not.toContain('이 주문으로 500원');
  });

  it('⚠ 거절이 있으면 «거절됨» 이 이긴다 — 부분 반품 뒤 재요청이 거절된 주문', async () => {
    const w = await open(partiallyReturned({
      returnRejectedReason: 'ZZ-사용 흔적이 있습니다',
      returnRejectedAt: '2026-08-25T03:00:00Z',
    }));

    // 둘 다 참인 상태다. 고객에게 급한 것은 «왜 거절됐나» 라 그쪽이 이겨야 한다.
    expect(w.text()).toContain('반품 요청이 거절됨');
    expect(w.text()).toContain('ZZ-사용 흔적이 있습니다');
  });

  it('⚠ 대조군: 반품이 없는 주문은 카드도 반품 섹션도 **안 뜬다** (예전 화면 그대로)', async () => {
    const w = await open(order({ status: 'DELIVERED', earnedPoint: 500 }));

    expect(w.text()).not.toContain('일부 반품 완료');
    expect(w.text()).not.toContain('반품된 품목');
    expect(w.text()).toContain('결제 금액');
    expect(w.text()).not.toContain('남은 결제 금액');
    // 회수가 없으면 원본을 그대로 말한다.
    expect(w.text()).toContain('이 주문으로');
    expect(w.text()).toContain('500원');
  });
});

/**
 * 반품 기한 (2026-08-27, BACKLOG §I-9) — **화면이 판정하지 않는다**.
 *
 * 🔴 §I-9 이 정한 것: **버튼을 숨기지 않고 «왜 안 되는지» 를 말한다.** 있던 것이 그냥 사라지면
 *    고객은 «화면이 고장 났다»고 읽는다 — 발송 후 취소 버튼을 «안 그리는» 것과 반대 방향인데,
 *    그쪽은 «될 것처럼 보여 주지 않는다» 이고 이쪽은 «있던 것이 사라졌다» 라서다.
 *
 * ⚠ **여기가 지키는 것은 «되나 안 되나» 가 아니다** — 그건 서버가 `returnRequestable` 로 답한다.
 *    화면이 `returnDeadline` 을 «지금» 과 비교해 다시 판정하면 **서버와 두 벌**이 되고,
 *    시계가 어긋난 기기에서 둘이 갈린다. 그래서 **날짜가 과거인지 미래인지로 시험하지 않는다.**
 */
describe('OrderDetailView — 반품 기한 (§I-9)', () => {
  let w;

  const delivered = (overrides = {}) => order({ status: 'DELIVERED', ...overrides });

  beforeEach(() => { authState.user = { id: ME, role: 'USER' }; push.mockReset(); });
  afterEach(() => { if (w) w.unmount(); w = null; authState.user = null; });

  async function open(data) {
    getOrder.mockReset().mockResolvedValue(data);
    w = mount(OrderDetailView, {
      props: { id: 'o1' },
      global: { stubs: { RouterLink: true, ItemThumb: true } },
    });
    await flushPromises();
    return w;
  }

  const returnBtn = (w) => w.findAll('button').find((b) => b.text() === '반품 요청');

  it('기한 안이면 버튼이 그대로 눌린다', async () => {
    const w = await open(delivered({ returnRequestable: true }));

    expect(returnBtn(w).attributes('disabled')).toBeUndefined();
    expect(w.text()).not.toContain('반품 가능 기간이 지났습니다');
  });

  it('🔴 기한이 지나도 버튼이 **사라지지 않는다** — 막히되 보인다', async () => {
    const w = await open(delivered({ returnRequestable: false }));

    // 있던 것이 그냥 없어지면 «화면이 고장 났다»로 읽힌다.
    expect(returnBtn(w)).toBeTruthy();
    expect(returnBtn(w).attributes('disabled')).toBeDefined();
  });

  it('🔴 «왜 안 되는지» 를 마감 날짜와 함께 말한다 — 툴팁이 아니라 보이는 줄로', async () => {
    const w = await open(delivered({
      returnRequestable: false,
      returnDeadline: '2026-07-31T12:00:00Z',
    }));

    // ⚠ 터치 기기엔 툴팁이 없다 — 본문에 실제로 있어야 한다.
    expect(w.text()).toContain('반품 가능 기간이 지났습니다');
    expect(w.text()).toContain('2026');
  });

  it('마감 시각을 서버가 안 주면 날짜를 지어내지 않는다', async () => {
    const w = await open(delivered({ returnRequestable: false, returnDeadline: null }));

    expect(w.text()).toContain('지금은 반품을 요청할 수 없습니다');
    expect(w.text()).not.toContain('까지');
  });

  it('⚠ 배송완료 «전» 에는 기한 안내를 안 띄운다 — 그건 «아직» 이지 «지났다» 가 아니다', async () => {
    const w = await open(order({ status: 'PAID', returnRequestable: false, returnDeadline: null }));

    expect(w.text()).not.toContain('반품을 요청할 수 없습니다');
  });
});

/**
 * 관리자 대행 반품 요청 (2026-08-27, BACKLOG §I-15).
 *
 * 🔴 **§I-9 이 만든 구멍을 메우는 경로다** — 7일 기한을 걸면서 「넘긴 건을 구제할 자리」가
 *    사라졌고, 이 버튼이 그 자리다. 그래서 `returnRequestable`(고객이 지금 걸 수 있나)을
 *    **안 본다** — 기한이 지났을 때가 이 버튼의 존재 이유다.
 */
describe('OrderDetailView — 관리자 대행 반품 요청 (§I-15)', () => {
  let w;

  const othersDelivered = (overrides = {}) => order({
    status: 'DELIVERED', memberId: 'someone-else', buyerNickname: 'ZZ구매자', ...overrides,
  });

  beforeEach(() => {
    authState.user = { id: 'admin-1', role: 'ADMIN' };
    push.mockReset();
    // 🔴 **API 목을 비운다** — 이 묶음은 «어느 API 로 갔나» 를 단언하므로, 앞 테스트의 호출이
    //    남아 있으면 `not.toHaveBeenCalled()` 가 **거짓으로 빨개진다**(실제로 그렇게 걸렸다).
    requestReturn.mockReset();
    requestReturnByAdmin.mockReset();
  });
  afterEach(() => { if (w) w.unmount(); w = null; authState.user = null; });

  async function open(data) {
    getOrder.mockReset().mockResolvedValue(data);
    w = mount(OrderDetailView, {
      props: { id: 'o1' },
      global: { stubs: { RouterLink: true, ItemThumb: true } },
    });
    await flushPromises();
    return w;
  }

  const btn = (w, text) => w.findAll('button').find((b) => b.text() === text);

  it('🔴 기한이 지난 주문에도 «반품 대행 접수» 가 보인다 — 그때가 존재 이유다', async () => {
    const w = await open(othersDelivered({ returnRequestable: false }));

    expect(btn(w, '반품 대행 접수')).toBeTruthy();
  });

  it('⚠ 관리자 «본인» 주문에는 안 뜬다 — 위쪽 「반품 요청」이 이미 있다', async () => {
    const w = await open(order({ status: 'DELIVERED', memberId: ME }));
    authState.user = { id: ME, role: 'ADMIN' };
    await flushPromises();

    expect(btn(w, '반품 대행 접수')).toBeFalsy();
  });

  it('🔴 대행 폼은 «누구 대신인지» 와 «기한을 무시한다» 를 말한다', async () => {
    const w = await open(othersDelivered({ returnRequestable: false }));
    await btn(w, '반품 대행 접수').trigger('click');
    await flushPromises();

    expect(w.text()).toContain('ZZ구매자');
    expect(w.text()).toContain('반품 가능 기간이 지났어도 접수됩니다');
  });

  it('🔴 대행 버튼은 «대행 API» 로 보낸다', async () => {
    const w = await open(othersDelivered({ returnRequestable: false }));
    await btn(w, '반품 대행 접수').trigger('click');
    await flushPromises();
    // ⚠ 선택자는 기존 반품 폼 테스트와 **같은 것**을 쓴다 — 사유는 number 가 아닌 input,
    //    제출 버튼은 «반품 요청» 중 **마지막** 것이다(여는 버튼과 글자가 같다).
    await w.find('input:not([type="number"])').setValue('ZZ-CS 사정');
    await w.findAll('button').filter((b) => b.text() === '반품 요청').at(-1).trigger('click');
    await flushPromises();

    expect(requestReturnByAdmin).toHaveBeenCalled();
    expect(requestReturn).not.toHaveBeenCalled();
  });

  it('🔴 고객의 «반품 요청» 은 대행 API 로 새지 않는다 — click 이벤트가 인자로 들어가던 자리', async () => {
    // ⚠ 실제로 낸 실수다(2026-08-27): `@click="openReturnForm"` 로 쓰면 Vue 가 **MouseEvent 를
    //    첫 인자로** 넘겨 byAdmin 이 truthy 가 되고, **고객 반품이 전부 대행 경로로 나간다**(403).
    authState.user = { id: ME, role: 'USER' };
    const w = await open(order({ status: 'DELIVERED', memberId: ME }));
    await btn(w, '반품 요청').trigger('click');
    await flushPromises();
    await w.find('input:not([type="number"])').setValue('ZZ-단순 변심');
    await w.findAll('button').filter((b) => b.text() === '반품 요청').at(-1).trigger('click');
    await flushPromises();

    expect(requestReturn).toHaveBeenCalled();
    expect(requestReturnByAdmin).not.toHaveBeenCalled();
  });
});
