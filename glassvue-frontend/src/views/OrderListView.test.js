import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 주문 «목록»(고객) — 부분 취소·반품이 목록에 보이는가 (2026-08-27, BACKLOG §I-7).
//
// 🔴 **이 화면에 테스트가 없었다.** 그리고 여기가 오래 틀려 있었다 — 서버는 진작부터
//    `cancelledQuantity`·`returnedQuantity`·`remainingQuantity` 를 내려주고 있었는데
//    (`myOrders` 가 full `OrderResponse` 를 준다) **화면이 원본 수량만 그렸다.**
//    그래서 **같은 주문을 목록과 상세에서 열면 다른 숫자가 나왔다.**
//
// ⚠ **표시 규칙 자체는 여기서 안 본다** — 그건 `OrderItemPartialNote` 하나에 있고 상세도 같은 것을
//    쓴다. 여기가 지키는 것은 «목록이 그 컴포넌트를 실제로 그리는가» 와 «합계가 남은 값을 말하는가» 다.
//
// ✅ **2026-09-02 에 나머지 «배선» 을 채웠다 — 상태 탭 · 페이징 · 빈 목록**(아래 셋째 describe 부터).
//    ⚠ 08-27 에 «여전히 안 덮였다» 고 적힌 자리이고, 그 문장이 **이월에서 엿새를 물려받혔다**
//    (08-27 → 08-28 → 09-01 → 오늘). `OrderAdminView` 가 09-01 에 같은 길을 갔다.
//    🔴 채운 이유는 «비어 있어서» 가 아니다 — 이 셋은 전부 **서버와 맞춰야 도는 배선**이다:
//    탭은 상태값을 서버에 그대로 넘기고(`?status=`), 페이징은 서버가 준 `page`·`last` 를 그대로 믿으며,
//    빈 목록은 «필터 때문에 빈 것» 과 «원래 없는 것» 을 갈라 말한다(2026-07-20 §8-7 사고의 산물).
//    ⚠ **셋 다 조용히 틀릴 수 있는 종류다** — 화면은 멀쩡히 그려지고 목록만 틀린 것을 보여 준다.

const fetchOrders = vi.fn();

vi.mock('../api/order', async (importOriginal) => {
  // ⚠ 상태 라벨·클래스는 **진짜를 쓴다**(OrderAdminView.test.js 와 같은 이유).
  const real = await importOriginal();
  return { ...real, fetchOrders: (...a) => fetchOrders(...a) };
});

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));

import OrderListView from './OrderListView.vue';

/** 품목 하나. 기본은 «아무것도 안 빠진 3개». */
function item(overrides = {}) {
  return {
    productId: 'p1', variantId: 'v1', productName: 'ZZ상품', optionName: null,
    price: 10_000, quantity: 3, lineTotal: 30_000,
    cancelledQuantity: 0, returnedQuantity: 0, returnRequestedQuantity: 0, remainingQuantity: 3,
    ...overrides,
  };
}

function order(overrides = {}, items = [item()]) {
  return {
    id: 'o1', orderNo: '20260827-0001', status: 'DELIVERED',
    createdAt: '2026-08-27T00:00:00Z', items,
    totalPrice: 30_000, payAmount: 33_000,
    cancelledItemsTotal: 0, returnedItemsTotal: 0,
    ...overrides,
  };
}

/**
 * ⚠ **주문 카드 안의 글자만** 본다. 화면 전체를 보면 상태 탭의 라벨(「취소됨」·「반품완료」)까지
 * 걸려서, 멀쩡한 주문에도 «취소됨» 이 있다고 나온다 — 처음 이렇게 썼다가 걸렸다(2026-08-27).
 */
const cardText = (w) => w.find('li.card').text();

async function open(orders = [order()], page = {}) {
  // ⚠ 기본은 «한 쪽짜리 목록» 이다 — 기존 §I-7 테스트 여섯이 이 기본값 위에 서 있다.
  fetchOrders.mockResolvedValue({ content: orders, totalPages: 1, page: 0, last: true, ...page });
  const w = mount(OrderListView);
  await flushPromises();
  return w;
}

/**
 * 상태 탭만 고른다.
 * ⚠ **텍스트만으로 고르면 안 된다** — 빈 목록의 「전체 보기」 버튼이 「전체」 탭과 앞부분이 겹치고,
 *   상태 라벨(「취소됨」·「반품완료」)은 카드 안 흔적 줄에도 나온다(이 파일 위쪽 `cardText` 주석과 같은 함정).
 * → 탭에만 있는 `aria-current` 로 범위를 좁힌 뒤 **정확히** 맞춘다.
 *   (OrderAdminView 와 달리 여기 탭에는 건수 배지가 없어 `startsWith` 가 아니라 `===` 다.)
 */
const tab = (w, text) => w.findAll('button[aria-current]').find((b) => b.text() === text);

const btn = (w, text) => w.findAll('button').find((b) => b.text() === text);

/**
 * 🔴 **기다릴 것은 «렌더» 가 아니라 «다시 불렀나» 다** (2026-09-01, OrderAdminView.test.js 와 같은 이유).
 * 탭을 눌러도 이전 목록이 그대로 그려져 있어서 «그려졌나» 로는 즉시 돌아온다.
 * ⚠ `flushPromises()` 를 정해진 횟수만큼 부르는 것은 «몇 번이면 되겠지» 라는 **추측**이다(08-27 교훈).
 */
async function untilCalledAgain(fn, tries = 50) {
  for (let i = 0; i < tries; i += 1) {
    if (fn.mock.calls.length > 0) return;
    await flushPromises();
    await new Promise((resolve) => { setTimeout(resolve, 0); });
  }
  throw new Error('목록을 다시 부르지 않았다 — 기다림을 포기했다');
}

beforeEach(() => { vi.clearAllMocks(); });

describe('OrderListView — 부분 취소·반품 표시 (§I-7)', () => {

  it('멀쩡한 주문에는 흔적 줄이 아예 안 그려진다', async () => {
    const w = await open();

    expect(cardText(w)).toContain('ZZ상품');
    expect(cardText(w)).not.toContain('취소됨');
    expect(cardText(w)).not.toContain('반품됨');
    // 🔴 「3개 중 0개」 같은 빈 말이 남으면 안 된다 — 멀쩡한 주문이 사고처럼 읽힌다.
    expect(cardText(w)).not.toContain('0개 ');
  });

  it('🔴 3개 중 1개 반품된 주문이 목록에서 그렇게 읽힌다 — 상세로 들어가야만 알던 자리', async () => {
    const w = await open([order(
      { returnedItemsTotal: 10_000, payAmount: 23_000 },
      [item({ returnedQuantity: 1, remainingQuantity: 2 })],
    )]);

    expect(cardText(w)).toContain('3개 중 1개');
    expect(cardText(w)).toContain('반품됨');
    // 원본 수량은 안 지운다 — 「3개 중 1개」가 읽히려면 둘 다 필요하다.
    expect(cardText(w)).toContain('× 3');
    // 남은 금액을 아래 줄에 적는다(10,000 × 2).
    expect(cardText(w)).toContain('20,000');
  });

  it('취소와 반품이 «줄을 나눠» 보인다 — 한 줄로 합쳐지면 무엇이 왜 빠졌는지 못 읽는다', async () => {
    const w = await open([order({}, [
      item({ quantity: 3, cancelledQuantity: 1, returnedQuantity: 1, remainingQuantity: 1 }),
    ])]);

    expect(cardText(w)).toContain('3개 중 1개 취소됨');
    expect(cardText(w)).toContain('3개 중 1개 반품됨');
  });

  it('전량이 빠진 품목은 금액에 줄이 그어진다', async () => {
    const w = await open([order({}, [item({ returnedQuantity: 3, remainingQuantity: 0 })])]);

    expect(cardText(w)).toContain('전량 반품됨');
    expect(w.find('.line-through').exists()).toBe(true);
  });

  it('🔴 합계가 «남은» 값을 말한다 — 목록이 원본을, 상세가 남은 값을 말하던 어긋남', async () => {
    // 부분 취소가 있으면 서버가 payAmount 를 이미 깎아 준다. 목록은 그 값을 그대로 쓴다.
    const w = await open([order(
      { cancelledItemsTotal: 10_000, payAmount: 23_000 },
      [item({ cancelledQuantity: 1, remainingQuantity: 2 })],
    )]);

    expect(cardText(w)).toContain('남은 결제 금액');
    expect(cardText(w)).toContain('23,000');
    // ⚠ 원본 상품합계(30,000)를 합계 자리에 그리면 안 된다 — 그게 고치기 전 모습이다.
    expect(cardText(w)).not.toContain('합계');
  });

  it('아무것도 안 빠진 주문은 「결제 금액」이라고 부른다', async () => {
    const w = await open();

    expect(cardText(w)).toContain('결제 금액');
    expect(cardText(w)).not.toContain('남은 결제 금액');
  });
});

/**
 * 상태 탭 — 화면이 서버에 **무엇을 달라고 하는가**.
 *
 * ⚠ 여기서 «목록이 걸러졌는가» 는 안 본다. 화면은 클라이언트에서 거르지 않고 `?status=` 로 서버에
 *   넘긴다(`OrderListView.vue` 머리 주석). 그래서 지킬 것은 **나가는 요청**이다.
 */
describe('OrderListView — 상태 탭', () => {

  it('탭 목록이 **서버 상태 맵에서** 나온다 — 손으로 적은 목록이 아니다', async () => {
    const w = await open();

    // 「전체」 + ORDER_STATUS_TEXT 의 일곱.
    const labels = w.findAll('button[aria-current]').map((b) => b.text());
    expect(labels).toEqual([
      '전체', '결제대기', '결제완료', '발송완료', '배송완료', '취소됨', '반품요청', '반품완료',
    ]);
    // 🔴 이게 «손으로 맞춘 짝» 이 **아니라는** 것이 요점이다 — 서버에 상태가 늘면 탭도 저절로 는다.
    //    (OrderAdminView 의 처리 버튼은 반대로 손으로 맞춘 짝이라 09-01 에 따로 못 박아 뒀다.)
  });

  it('탭을 누르면 **그 상태로** 다시 부른다', async () => {
    const w = await open();
    fetchOrders.mockClear();

    await tab(w, '발송완료').trigger('click');
    await untilCalledAgain(fetchOrders);

    expect(fetchOrders).toHaveBeenCalledWith(expect.objectContaining({ status: 'SHIPPED' }));
  });

  it('「전체」 탭은 상태를 **비워서** 부른다 — 필터를 푸는 길이 있어야 한다', async () => {
    const w = await open();
    await tab(w, '취소됨').trigger('click');
    await untilCalledAgain(fetchOrders);
    fetchOrders.mockClear();

    await tab(w, '전체').trigger('click');
    await untilCalledAgain(fetchOrders);

    expect(fetchOrders).toHaveBeenCalledWith(expect.objectContaining({ status: null }));
  });

  it('🔴 탭을 바꾸면 **첫 쪽으로 돌아간다** — 3쪽을 보다 옮겼는데 그 상태의 3쪽을 달라고 하면 안 된다', async () => {
    // 3쪽짜리 목록의 **셋째 쪽**을 보고 있는 상태로 연다.
    const w = await open([order()], { page: 2, totalPages: 3, last: true });
    fetchOrders.mockClear();

    await tab(w, '결제완료').trigger('click');
    await untilCalledAgain(fetchOrders);

    // ⚠ 「결제완료」가 한 쪽뿐이면 3쪽을 달라는 요청은 **빈 목록**으로 돌아온다 —
    //    화면은 멀쩡하고 «주문이 없다» 고만 말한다. 고장으로 안 보이는 종류다.
    expect(fetchOrders).toHaveBeenCalledWith(expect.objectContaining({ status: 'PAID', page: 0 }));
  });
});

/**
 * 페이징 — 서버가 준 `page`·`totalPages`·`last` 를 **그대로 믿는다**.
 * ⚠ 화면이 스스로 «마지막인가» 를 셈하지 않는 것이 요점이다(셈하면 서버와 갈린다).
 */
describe('OrderListView — 페이징', () => {

  it('한 쪽뿐이면 페이지 이동이 **아예 안 그려진다**', async () => {
    const w = await open([order()], { page: 0, totalPages: 1, last: true });

    expect(btn(w, '이전')).toBeUndefined();
    expect(btn(w, '다음')).toBeUndefined();
  });

  it('여러 쪽이면 「n / 전체」를 적고, 첫 쪽에서 「이전」이 잠긴다', async () => {
    const w = await open([order()], { page: 0, totalPages: 3, last: false });

    // 사람이 세는 번호는 1부터다 — 서버의 0-based 를 그대로 적으면 «0쪽» 이 된다.
    expect(w.text()).toContain('1 / 3');
    expect(btn(w, '이전').attributes('disabled')).toBeDefined();
    expect(btn(w, '다음').attributes('disabled')).toBeUndefined();
  });

  it('🔴 마지막 쪽에서 「다음」이 잠긴다 — `last` 를 화면이 다시 셈하지 않는다', async () => {
    const w = await open([order()], { page: 2, totalPages: 3, last: true });

    expect(w.text()).toContain('3 / 3');
    expect(btn(w, '다음').attributes('disabled')).toBeDefined();
    expect(btn(w, '이전').attributes('disabled')).toBeUndefined();
  });

  it('「다음」을 누르면 **다음 쪽을** 달라고 한다', async () => {
    const w = await open([order()], { page: 0, totalPages: 3, last: false });
    fetchOrders.mockClear();

    await btn(w, '다음').trigger('click');
    await untilCalledAgain(fetchOrders);

    expect(fetchOrders).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }));
  });

  it('🔴 쪽을 넘겨도 **상태 필터가 따라간다** — 풀리면 다른 상태의 주문이 섞여 나온다', async () => {
    const w = await open([order()], { page: 0, totalPages: 3, last: false });

    await tab(w, '반품요청').trigger('click');
    await untilCalledAgain(fetchOrders);
    fetchOrders.mockClear();

    await btn(w, '다음').trigger('click');
    await untilCalledAgain(fetchOrders);

    // ⚠ 「2쪽」인데 상태가 빠지면 **전체 주문의 2쪽**이 온다 — 개수가 그럴듯해서 안 들킨다.
    expect(fetchOrders).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'RETURN_REQUESTED', page: 1 }),
    );
  });
});

/**
 * 빈 목록 — 🔴 **«필터 때문에 빈 것» 과 «원래 없는 것» 은 다른 말을 해야 한다.**
 * 2026-07-20 §8-7 사고의 산물이다(기본 필터로 빈 화면을 «고장» 으로 오해했다).
 * 그 판단이 `EmptyState` 주석에는 적혀 있는데 **이 화면에서 지켜지는지는 아무도 안 보고 있었다.**
 */
describe('OrderListView — 빈 목록', () => {

  it('주문이 하나도 없으면 «아직 주문 내역이 없어요» 라고 말하고 상품으로 보낸다', async () => {
    const w = await open([]);

    expect(w.text()).toContain('아직 주문 내역이 없어요');
    expect(btn(w, '상품 보러 가기')).toBeDefined();
    // ⚠ 필터를 안 걸었으니 「전체 보기」는 나오면 안 된다 — 풀 필터가 없는데 푸는 버튼을 주는 셈이다.
    expect(btn(w, '전체 보기')).toBeUndefined();
  });

  it('🔴 탭 때문에 빈 것은 **어느 탭인지 대고** 말한다', async () => {
    const w = await open([order()]);
    fetchOrders.mockResolvedValue({ content: [], totalPages: 0, page: 0, last: true });

    await tab(w, '발송완료').trigger('click');
    await flushPromises();

    expect(w.text()).toContain('‘발송완료’ 상태인 주문이 없어요');
    expect(w.text()).not.toContain('아직 주문 내역이 없어요');
  });

  it('🔴 「전체 보기」가 **실제로 필터를 푼다** — 여기가 죽으면 막다른 길이 된다', async () => {
    const w = await open([order()]);
    fetchOrders.mockResolvedValue({ content: [], totalPages: 0, page: 0, last: true });
    await tab(w, '발송완료').trigger('click');
    await flushPromises();
    fetchOrders.mockClear();

    await btn(w, '전체 보기').trigger('click');
    await untilCalledAgain(fetchOrders);

    // ⚠ 빈 화면에서 빠져나가는 **유일한** 길이다(J 축이 본 «막다른 길» 과 같은 모양).
    expect(fetchOrders).toHaveBeenCalledWith(expect.objectContaining({ status: null, page: 0 }));
  });

  it('🔴 불러오는 중에는 «없어요» 가 **안 스친다** — 빈 상태가 깜빡이면 없는 사고를 본 것이 된다', async () => {
    // 응답을 붙잡아 둔 채로 마운트한다 — 로딩 상태를 실제로 만든다.
    let release;
    fetchOrders.mockReturnValue(new Promise((resolve) => { release = resolve; }));
    const w = mount(OrderListView);
    await flushPromises();

    expect(w.text()).not.toContain('없어요');
    expect(w.find('.skeleton').exists()).toBe(true);

    release({ content: [order()], totalPages: 1, page: 0, last: true });
    await flushPromises();
    expect(w.find('.skeleton').exists()).toBe(false);
  });
});
