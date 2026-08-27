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

async function open(orders = [order()]) {
  fetchOrders.mockResolvedValue({ content: orders, totalPages: 1, page: 0, last: true });
  const w = mount(OrderListView);
  await flushPromises();
  return w;
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
