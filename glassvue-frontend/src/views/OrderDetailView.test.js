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

vi.mock('../api/order', () => ({
  getOrder: (...a) => getOrder(...a),
  cancelOrderItem: (...a) => cancelOrderItem(...a),
  cancelOrderItemByAdmin: (...a) => cancelOrderItemByAdmin(...a),
  payOrder: vi.fn(), shipOrder: vi.fn(), deliverOrder: vi.fn(), cancelOrder: vi.fn(),
  requestReturn: (...a) => requestReturn(...a),
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
});
