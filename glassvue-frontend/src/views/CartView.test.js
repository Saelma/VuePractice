import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 장바구니(고객) — **배선**만 본다 (2026-09-02).
//
// 🔴 **이 화면에 테스트가 0건이었다.** 여기가 지키는 것 셋:
//   ① 🛒 **헤더 배지 동기화** — 수량변경·삭제·비우기가 «전부 `load()` 를 거친다» 는 구조.
//      ⚠ **셋을 각각 밟는다** — `changeQty` 와 `onRemove` 는 **다른 함수**라 한쪽만 덮으면
//        나머지의 `load()` 가 빠져도 안 잡힌다(2026-09-02 리뷰가 지적한 자리).
//      그 구조가 깨져도 **화면은 멀쩡히 돌고 헤더 숫자만 낡는다.**
//   ② **금액은 서버가 준 것을 그대로 그린다** — 화면이 다시 셈하지 않는다(§I-7 과 같은 각도).
//   ③ **주문서로 넘기기 전에 살 수 없는 항목을 막는다** — 갔다가 되돌아오지 않게.
//
// ⚠ **무료배송 진행률(`freeShipPct`)만은 화면이 «계산» 한다** — 서버가 준
//    `amountUntilFree` 로 임계값을 되짚는 식이라, 그 식이 틀리면 진행바가 조용히 거짓말한다.

const push = vi.fn();
vi.mock('vue-router', () => ({ useRouter: () => ({ push: (...a) => push(...a) }) }));

const getCart = vi.fn();
const updateCartItem = vi.fn();
const removeCartItem = vi.fn();
const clearCart = vi.fn();
vi.mock('../api/cart', () => ({
  getCart: (...a) => getCart(...a),
  updateCartItem: (...a) => updateCartItem(...a),
  removeCartItem: (...a) => removeCartItem(...a),
  clearCart: (...a) => clearCart(...a),
}));

const setCartCount = vi.fn();
vi.mock('../stores/cart', () => ({ setCartCount: (...a) => setCartCount(...a) }));

import CartView from './CartView.vue';

/**
 * 🔴 **`CartItemResponse` 의 칸 이름을 그대로 쓴다** — `name`·`thumbUrl` 이다.
 * ⚠ 주문 DTO(`OrderItemResponse`)는 `productName`·`productImageUrl` 이라 **이름이 다르다.**
 *    처음엔 `OrderDetailView.test.js` 에서 픽스처를 베껴 와 주문 쪽 이름을 썼는데,
 *    화면은 `item.name` 을 읽으므로 **모든 줄이 이름 없이 렌더되면서도 테스트는 초록**이었다
 *    (금액만 단언했기 때문). 🔴 **베낀 픽스처는 계약까지 베끼지 않는다.**
 */
function item(overrides = {}) {
  return {
    productId: 'p1', variantId: 'v1', name: 'ZZ장바구니상품', optionName: null,
    price: 10_000, regularPrice: 10_000, quantity: 2, lineTotal: 20_000,
    available: true, thumbUrl: null,
    ...overrides,
  };
}

/**
 * ⚠ **`payAmount` 를 «상품합계 + 배송비» 와 일부러 다르게 둔다**(20,000 + 3,000 ≠ 21,500).
 * 🔴 **오늘 실제로 그 값이 같아서 «화면이 다시 더한다» 를 못 잡았다** — 검증하려는 것이
 * «서버 값을 그대로 그리는가» 이므로, 그 값은 **화면에서 재구성 불가능해야** 한다.
 * ⚠ 지금은 둘이 같은 것이 정상이지만(장바구니엔 쿠폰·적립금이 없다), 서버가 나중에
 * 무언가를 더 빼면 그때 갈리는 자리라 **지금부터 출처를 못 박아 둔다.**
 */
function cart(overrides = {}) {
  return {
    items: [item()], totalQuantity: 2, totalPrice: 20_000,
    shippingFee: 3_000, payAmount: 21_500, amountUntilFree: 7_400,
    ...overrides,
  };
}

async function open(data = cart()) {
  getCart.mockResolvedValue(data);
  const w = mount(CartView);
  await flushPromises();
  return w;
}

const btn = (w, text) => w.findAll('button').find((b) => b.text() === text);
/** 수량 ± 버튼은 글자가 없다 — 품목 줄 안에서 순서로 고른다(− 먼저, + 나중). */
const qtyBtns = (w) => w.findAll('button').filter((b) => b.classes().includes('h-8'));

beforeEach(() => { vi.clearAllMocks(); });
afterEach(() => { vi.restoreAllMocks(); });

describe('CartView — 헤더 배지 동기화', () => {

  it('열면 서버가 준 총수량으로 배지를 맞춘다', async () => {
    await open();
    expect(setCartCount).toHaveBeenCalledWith(2);
  });

  it('🔴 수량을 바꾸면 **다시 읽어** 배지를 맞춘다 — 화면이 숫자를 스스로 더하지 않는다', async () => {
    const w = await open();
    setCartCount.mockClear();
    // 🔴 **9 는 «2 + 1» 로 만들 수 없는 수다.** 처음엔 3 을 돌려줬는데 그게 곧 2+1 이라
    //    «화면이 스스로 더한다» 로 바꿔도 초록이었다 — 단언이 자기 주장을 못 반증했다.
    getCart.mockResolvedValue(cart({ totalQuantity: 9, totalPrice: 90_000, payAmount: 91_500 }));

    await qtyBtns(w)[1].trigger('click');   // +
    await flushPromises();

    expect(updateCartItem).toHaveBeenCalledWith('v1', 3);
    expect(setCartCount).toHaveBeenCalledWith(9);
  });

  it('🔴 수량을 1 아래로 내리면 **삭제**로 간다 — 0개짜리 줄을 만들지 않는다', async () => {
    const w = await open(cart({ items: [item({ quantity: 1, lineTotal: 10_000 })], totalQuantity: 1 }));

    await qtyBtns(w)[0].trigger('click');   // −

    expect(removeCartItem).toHaveBeenCalledWith('v1');
    expect(updateCartItem).not.toHaveBeenCalled();
  });

  it('비우면 배지가 0 이 되고 **목록도 비워진다**', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const w = await open();
    setCartCount.mockClear();
    getCart.mockResolvedValue(cart({ items: [], totalQuantity: 0, totalPrice: 0, payAmount: 0 }));

    await btn(w, '장바구니 비우기').trigger('click');
    await flushPromises();

    expect(clearCart).toHaveBeenCalled();
    expect(setCartCount).toHaveBeenCalledWith(0);
    // 🔴 배지만 보면 «다시 읽었다» 와 «0 을 손으로 넣었다» 를 못 가른다 —
    //    화면이 실제로 갱신됐는지까지 본다(안 그러면 지운 줄이 그대로 남는다).
    expect(w.text()).toContain('장바구니가 비어 있어요');
  });

  it('🔴 「삭제」로 지워도 다시 읽어 배지를 맞춘다 — `changeQty` 와 **다른 함수**다', async () => {
    const w = await open();
    setCartCount.mockClear();
    getCart.mockResolvedValue(cart({ items: [], totalQuantity: 0, totalPrice: 0, payAmount: 0 }));

    await btn(w, '삭제').trigger('click');
    await flushPromises();

    // ⚠ 「1 아래로 내리면 삭제」 테스트는 `changeQty` 를 지난다 — 이 버튼은 `onRemove` 다.
    //    한쪽만 덮으면 나머지 한쪽의 `load()` 가 빠져도 안 잡힌다.
    expect(removeCartItem).toHaveBeenCalledWith('v1');
    expect(setCartCount).toHaveBeenCalledWith(0);
  });

  it('⚠ 비우기를 취소하면 **아무 일도 안 일어난다**', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const w = await open();

    await btn(w, '장바구니 비우기').trigger('click');
    await flushPromises();

    expect(clearCart).not.toHaveBeenCalled();
  });
});

describe('CartView — 금액은 서버가 준 것을 그린다', () => {

  it('🔴 결제 금액은 `payAmount` 다 — 상품합계+배송비를 화면이 다시 더하지 않는다', async () => {
    const w = await open();
    // 21,500 은 20,000 + 3,000 이 아니다 — 다시 더하면 23,000 이 나와 여기서 갈린다.
    expect(w.text()).toContain('21,500');
    expect(w.text()).not.toContain('23,000');
  });

  it('무료배송까지 남은 금액을 그대로 말한다', async () => {
    const w = await open();
    // ⚠ 7,400 은 픽스처의 다른 어떤 값과도 안 겹친다 — 처음엔 10,000 이라
    //    품목 단가(10,000)가 이미 그 글자를 화면에 올려 놓고 있었다.
    expect(w.text()).toContain('7,400');
    expect(w.text()).toContain('더 담으면');
  });

  it('품목의 **이름과 옵션**을 그린다 — 칸 이름이 어긋나면 빈 줄이 된다', async () => {
    const w = await open(cart({ items: [item({ optionName: '검정 / M' })] }));
    // 🔴 `CartItemResponse` 는 `name` 이다(주문 DTO 의 `productName` 이 아니다).
    expect(w.text()).toContain('ZZ장바구니상품');
    expect(w.text()).toContain('검정 / M');
  });

  it('🔴 남은 금액이 0 이면 «무료배송이 적용됐어요» 로 바뀌고 진행바가 100% 다', async () => {
    const w = await open(cart({ shippingFee: 0, payAmount: 20_000, amountUntilFree: 0 }));

    expect(w.text()).toContain('무료배송');
    expect(w.text()).not.toContain('더 담으면');
    expect(w.find('[role="progressbar"]').attributes('aria-valuenow')).toBe('100');
  });

  it('⚠ 진행률은 «임계값을 되짚어» 낸다 — 20,000 / (20,000+7,400) = 73%', async () => {
    const w = await open();
    // 🔴 서버는 임계값을 안 준다. 화면이 `총액 + 남은 금액` 으로 되짚는 것이 유일한 근거다.
    expect(w.find('[role="progressbar"]').attributes('aria-valuenow')).toBe('73');
  });
});

describe('CartView — 주문서로 넘기기 전 가드', () => {

  it('🔴 살 수 없는 항목이 있으면 **안 넘어간다** — 주문서까지 갔다가 되돌아오지 않게', async () => {
    const w = await open(cart({ items: [item({ available: false })] }));

    await btn(w, '주문하기').trigger('click');
    await flushPromises();

    expect(push).not.toHaveBeenCalled();
  });

  it('멀쩡하면 주문서로 넘어간다', async () => {
    const w = await open();

    await btn(w, '주문하기').trigger('click');
    await flushPromises();

    expect(push).toHaveBeenCalledWith('/checkout');
  });

  it('빈 장바구니는 «비어 있어요» 를 말하고 상품으로 보낸다', async () => {
    const w = await open(cart({ items: [], totalQuantity: 0, totalPrice: 0, payAmount: 0 }));

    expect(w.text()).toContain('장바구니가 비어 있어요');
    expect(btn(w, '상품 보러 가기')).toBeDefined();
  });
});
