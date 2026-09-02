import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 주문서(고객) — **배선**만 본다 (2026-09-02, BACKLOG §K/C 후속).
//
// 🔴 **이 화면에 테스트가 0건이었다.** 그리고 여기는 «돈을 두 번 계산하는» 자리다 —
//    적립금 상한(`maxUsablePoint`)이 **서버 규칙의 두 번째 사본**이고, 그 사실을
//    코드 주석이 스스로 적어 뒀다(*"서버와 **같은 규칙**이어야 한다"*).
//
// ⚠ **상한 «식» 자체는 여기서 안 본다** — `api/point.test.js` 가 4건으로 이미 덮었고
//    그중 하나는 제목이 *"서버의 상한 규칙과 같아야 한다"* 다.
//    🔴 **여기가 지키는 것은 «화면이 그 함수를 실제로 부르는가» 와 «서버에 무엇을 보내는가» 다.**
//    함수가 맞아도 화면이 안 부르면 소용없고, 그건 §I-2 가 2주를 간 모양이다.
//
// ⚠ **계산 함수는 진짜를 쓴다**(OrderDetailView.test.js 와 같은 이유) — 가짜로 갈아끼우면
//    화면이 그 규칙을 쓴다는 사실이 검증에서 빠진다.

const push = vi.fn();
vi.mock('vue-router', () => ({ useRouter: () => ({ push: (...a) => push(...a) }) }));

const setCartCount = vi.fn();
vi.mock('../stores/cart', () => ({ setCartCount: (...a) => setCartCount(...a) }));

const getCart = vi.fn();
const checkout = vi.fn();
const fetchMyCoupons = vi.fn();
const fetchPointAccount = vi.fn();
const fetchAddresses = vi.fn();
const updateShippingAddress = vi.fn();

vi.mock('../api/cart', () => ({ getCart: (...a) => getCart(...a) }));
vi.mock('../api/order', () => ({ checkout: (...a) => checkout(...a) }));
vi.mock('../api/coupon', () => ({ fetchMyCoupons: (...a) => fetchMyCoupons(...a) }));
vi.mock('../api/member', () => ({ updateShippingAddress: (...a) => updateShippingAddress(...a) }));
vi.mock('../api/address', async (importOriginal) => ({
  ...(await importOriginal()),
  fetchAddresses: (...a) => fetchAddresses(...a),
}));
// 🔴 `maxUsablePoint`·`clampPoint` 는 **진짜다** — 이 파일의 검증 대상이 그것을 부르는가이다.
vi.mock('../api/point', async (importOriginal) => ({
  ...(await importOriginal()),
  fetchPointAccount: (...a) => fetchPointAccount(...a),
}));

import CheckoutView from './CheckoutView.vue';
import { authState } from '../stores/auth';

/**
 * 상품합계 50,000 · 배송비 0(무료 기준을 넘겼다).
 * 🔴 **`CartItemResponse` 의 칸 이름이다** — `name`·`thumbUrl` 이고 주문 DTO 와 **다르다**.
 * ⚠ `productId` 도 넣는다 — 템플릿이 `:key` 로 쓴다.
 */
function cartItem(overrides = {}) {
  return {
    productId: 'p1', variantId: 'v1', name: 'ZZ주문서상품', optionName: null,
    price: 25_000, regularPrice: 25_000, quantity: 2, lineTotal: 50_000,
    available: true, thumbUrl: null,
    ...overrides,
  };
}

function cart(overrides = {}) {
  return {
    items: [cartItem()],
    totalQuantity: 2, totalPrice: 50_000, shippingFee: 0, payAmount: 50_000, amountUntilFree: 0,
    ...overrides,
  };
}

function coupon(discountPreview) {
  return { id: 'mc1', name: 'ZZ쿠폰', discountPreview, usable: true, expiresAt: null };
}

async function open({ cartData = cart(), coupons = [], balance = 100_000 } = {}) {
  getCart.mockResolvedValue(cartData);
  fetchMyCoupons.mockResolvedValue(coupons);
  fetchAddresses.mockResolvedValue([]);
  fetchPointAccount.mockResolvedValue(balance == null ? null : { balance, totalPurchase: 0, grade: 'BRONZE' });
  mounted = mount(CheckoutView);
  await flushPromises();
  return mounted;
}

/**
 * 배송지를 채운다 — 안 채우면 제출이 주소 검증에서 먼저 막힌다.
 * ⚠ **DevExtreme 입력은 `change` 에서 값이 확정된다**(AuditLogAdminView.test.js 실측).
 *    `setValue` 만으로는 v-model 이 안 움직여 «비어 있다» 로 남는다.
 */
async function type(w, placeholder, value) {
  const input = w.find(`input[placeholder="${placeholder}"]`);
  if (!input.exists()) throw new Error(`입력을 못 찾았다: ${placeholder}`);
  await input.setValue(value);
  await input.trigger('change');
  await flushPromises();
}

async function fillAddress(w) {
  await type(w, '홍길동', 'ZZ수령인');
  await type(w, '010-1234-5678', '010-1234-5678');
  await type(w, '06134', '06134');
  await type(w, '서울시 강남구 테헤란로 1', '서울시 강남구 테헤란로 1');
}

/** 쿠폰은 라디오다 — 값이 `memberCoupon.id` 다. */
async function pickCoupon(w, id) {
  const radio = w.findAll('input[type="radio"]').find((r) => r.element.value === id);
  if (!radio) throw new Error(`쿠폰 라디오를 못 찾았다: ${id}`);
  await radio.setValue();
  await flushPromises();
}

const btn = (w, text) => w.findAll('button').find((b) => b.text().includes(text));
/** 제출 버튼. ⚠ 「주문」이 아니라 **「결제하기」** 다(제출 중엔 「주문 중…」). */
const payBtn = (w) => btn(w, '결제하기') || btn(w, '주문 중');

let mounted = null;

beforeEach(() => {
  vi.clearAllMocks();
  authState.user = { id: 'm1', nickname: 'ZZ구매자', role: 'USER' };
});

/**
 * ⚠ **마운트한 것은 반드시 언마운트한다**(`AuditLogAdminView.test.js` 의 규약).
 * 이 화면은 `ShippingAddressFields` 로 DevExtreme 위젯을 여섯 개 띄운다 —
 * 🔴 **남겨 두면 jsdom 정리 뒤 `window.getComputedStyle is not a function` 이
 * «처리되지 않은 에러» 로 터지고, 그건 전수 실행에서만 보인다.**
 * 지금은 재현되지 않지만 **문서화된 고장 모양**이라 미리 지킨다.
 */
afterEach(() => {
  mounted?.unmount();
  mounted = null;
  vi.restoreAllMocks();
});

describe('CheckoutView — 적립금 상한 배선', () => {

  it('🔴 상한이 **상품합계**를 기준으로 잡힌다 — 배송비는 안 섞인다', async () => {
    // 배송비 3,000 짜리 장바구니. 상한이 payAmount(53,000)로 잡히면 안 된다.
    const w = await open({
      cartData: cart({ shippingFee: 3_000, payAmount: 53_000, amountUntilFree: 10_000 }),
      balance: 100_000,
    });

    // 잔액(100,000) > 상품합계(50,000) 이므로 상한은 50,000 이다.
    expect(w.text()).toContain('최대 50,000원까지');
    // ⚠ 서버 상한도 `cart.totalPrice - couponDiscount` 다 — 배송비를 넣으면 서버가 400 을 낸다.
    expect(w.text()).not.toContain('최대 53,000원까지');
  });

  it('🔴 쿠폰을 고르면 상한이 **그만큼 줄어든다** — 두 값이 손으로 맞춰진 짝이다', async () => {
    const w = await open({ coupons: [coupon(20_000)], balance: 100_000 });

    await pickCoupon(w, 'mc1');

    // 50,000 − 20,000 = 30,000
    expect(w.text()).toContain('최대 30,000원까지');
  });

  it('잔액이 상품합계보다 적으면 **잔액**이 상한이다', async () => {
    const w = await open({ balance: 7_000 });
    expect(w.text()).toContain('최대 7,000원까지');
  });

  it('⚠ 적립금을 못 읽어도 화면이 산다 — 주문을 막지 않는다', async () => {
    fetchPointAccount.mockRejectedValue(new Error('적립금 조회 실패'));
    getCart.mockResolvedValue(cart());
    fetchMyCoupons.mockResolvedValue([]);
    fetchAddresses.mockResolvedValue([]);
    mounted = mount(CheckoutView);
    const w = mounted;
    await flushPromises();

    // 🔴 「실패해도 주문은 되어야 한다」가 코드의 명시적 판단이다(`.catch(() => null)`).
    expect(w.text()).not.toContain('적립금 조회 실패');
    expect(payBtn(w)).toBeDefined();
  });
});

describe('CheckoutView — 요약이 장바구니를 그대로 읽는다', () => {

  it('품목 이름을 그린다 — 칸 이름이 어긋나면 빈 줄이 된다', async () => {
    const w = await open();
    // 🔴 장바구니 DTO 는 `name` 이다. 주문 DTO 의 `productName` 을 쓰면 여기가 빈다.
    expect(w.text()).toContain('ZZ주문서상품');
  });
});

describe('CheckoutView — 서버에 무엇을 보내는가', () => {

  it('🔴 쿠폰은 **id 만** 보낸다 — 할인액을 실으면 위조된다', async () => {
    const w = await open({ coupons: [coupon(20_000)] });
    await pickCoupon(w, 'mc1');
    await fillAddress(w);
    checkout.mockResolvedValue('o1');

    await payBtn(w).trigger('click');
    await flushPromises();

    const sent = checkout.mock.calls[0][0];
    expect(sent.memberCouponId).toBe('mc1');
    // 🔴 할인액은 안 보낸다 — 보내면 위조된다(서버가 다시 계산한다).
    expect(sent).not.toHaveProperty('couponDiscount');
    expect(sent).not.toHaveProperty('discount');
  });

  it('🔴 상한을 넘겨 입력하면 **잘린 값**이 나간다 — 화면 값이 그대로 가면 서버가 400 을 낸다', async () => {
    // 잔액 100,000 · 상품합계 50,000 → 상한 50,000. 거기에 90,000 을 적어 넣는다.
    const w = await open({ balance: 100_000 });
    const input = w.find('input[type="number"]');
    await input.setValue(90_000);
    await flushPromises();
    await fillAddress(w);
    checkout.mockResolvedValue('o1');

    await payBtn(w).trigger('click');
    await flushPromises();

    // 🔴 `appliedPoint`(=clampPoint) 를 보내야지 `usePoint`(입력 원본)를 보내면 안 된다.
    //    ⚠ 그대로 보내도 **서버가 막아 주므로 데이터는 안 상한다** — 대신 사용자가
    //       「결제하기」를 누른 뒤에야 400 을 본다. 화면 상한이 있는 이유가 그것이다.
    expect(checkout.mock.calls[0][0].usePoint).toBe(50_000);
  });

  it('🔴 적립금을 안 쓰면 `usePoint` 를 **null 로** 보낸다 — 0 과 «안 씀» 을 가른다', async () => {
    const w = await open();
    await fillAddress(w);
    checkout.mockResolvedValue('o1');

    await payBtn(w).trigger('click');
    await flushPromises();

    expect(checkout.mock.calls[0][0].usePoint).toBeNull();
  });

  it('🔴 배송 요청사항이 **주소록 저장에 안 섞인다** — 규율이 아니라 구조로 막은 자리', async () => {
    const w = await open();
    await fillAddress(w);
    await type(w, '예: 부재 시 경비실에 맡겨 주세요', 'ZZ-부재 시 경비실에');
    checkout.mockResolvedValue('o1');
    updateShippingAddress.mockResolvedValue(undefined);

    await payBtn(w).trigger('click');
    await flushPromises();

    // 🔴 기본 배송지가 없던 사람은 `saveAsDefault` 가 켜져 있어 저장이 실제로 돈다.
    expect(updateShippingAddress).toHaveBeenCalled();
    expect(updateShippingAddress.mock.calls[0][0]).not.toHaveProperty('shipMemo');
    // ⚠ 주문에는 실려야 한다 — 「안 섞인다」가 「안 보낸다」로 잘못 굳으면 기능이 죽는다.
    expect(checkout.mock.calls[0][0].shipMemo).toBe('ZZ-부재 시 경비실에');
  });
});

describe('CheckoutView — 주문이 된 뒤', () => {

  it('🔴 헤더 🛒 배지를 **0 으로 내리고** 주문 상세로 보낸다', async () => {
    const w = await open();
    await fillAddress(w);
    checkout.mockResolvedValue('o-123');

    await payBtn(w).trigger('click');
    await flushPromises();

    // ⚠ 이게 빠지면 **주문을 끝냈는데 헤더가 「2」라고 말한다** — 화면은 멀쩡히 넘어간다.
    //    🔴 `CartView` 는 이 불변식을 자기 파일의 머리에 걸어 뒀는데 여기만 비어 있었다
    //    (2026-09-02 리뷰가 «비대칭» 이라고 지적한 자리).
    expect(setCartCount).toHaveBeenCalledWith(0);
    expect(push).toHaveBeenCalledWith('/orders/o-123');
  });

  it('⚠ 주문이 실패하면 배지도 안 건드리고 이동도 안 한다', async () => {
    const w = await open();
    await fillAddress(w);
    checkout.mockRejectedValue(new Error('ZZ-주문 실패'));

    await payBtn(w).trigger('click');
    await flushPromises();

    expect(setCartCount).not.toHaveBeenCalled();
    expect(push).not.toHaveBeenCalled();
    expect(w.text()).toContain('ZZ-주문 실패');
  });
});

describe('CheckoutView — 제출 가드', () => {

  it('빈 장바구니로는 주문이 안 나간다', async () => {
    const w = await open({ cartData: cart({ items: [], totalQuantity: 0, totalPrice: 0, payAmount: 0 }) });
    await fillAddress(w);

    await payBtn(w).trigger('click');
    await flushPromises();

    expect(checkout).not.toHaveBeenCalled();
    expect(w.text()).toContain('장바구니가 비어 있어요');
  });

  it('🔴 품절 상품이 섞여 있으면 주문이 안 나간다 — 서버 400 을 화면이 먼저 막는다', async () => {
    const w = await open({
      cartData: cart({ items: [cartItem({ name: 'ZZ품절상품', available: false })] }),
    });
    await fillAddress(w);

    await payBtn(w).trigger('click');
    await flushPromises();

    expect(checkout).not.toHaveBeenCalled();
    expect(w.text()).toContain('구매할 수 없는 상품');
  });

  it('배송지가 비면 주문이 안 나간다', async () => {
    const w = await open();

    await payBtn(w).trigger('click');
    await flushPromises();

    expect(checkout).not.toHaveBeenCalled();
  });
});
