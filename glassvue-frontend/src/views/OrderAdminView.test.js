import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 열 번째 화면 테스트 (2026-08-25, BACKLOG §I-2) — **반품 승인·거절 자리만** 본다.
//
// 🔴 **이 화면에 테스트가 없어서 버그가 2주를 갔다.** 거절 버튼이 사유 없이 `rejectReturn(row.id)` 를
//    불렀는데, 서버는 V47(2026-08-11)부터 `@NotBlank` 라 **항상 400** 이었다. 같은 날 주문 **상세**
//    에는 사유 폼이 붙었고 **목록만 안 열렸다** — WA §1-2-1 의 «짝 중 한쪽만 고쳐진다» 그대로다.
//
// ✅ **2026-09-01 에 나머지를 채웠다** — 발송·배송완료·대행 취소·상태 탭.
//    ⚠ 08-25 에 «여기서 안 본다» 고 적어 둔 자리이고, 그 문장이 **이월에서 다섯 번 물려받혔다.**
//    🔴 채운 이유는 «비어 있어서» 가 아니다: 이 화면의 처리 버튼들은 **상태에 따라 나타났다 사라지는데**,
//    그 조건이 서버의 `isCancellable()` 같은 규칙과 **손으로 맞춰져 있다**(주석이 그렇게 말한다).
//    손으로 맞춘 짝은 한쪽만 움직이면 조용히 갈린다 — §I-2 가 정확히 그렇게 2주를 갔다.
//
// ⚠ DevExtreme 은 jsdom 에서 그대로 렌더된다(2026-08-14 실측, AuditLogAdminView.test.js 주석 참고).
//    `attachTo` 만 쓰지 않는다.

const fetchAdminOrders = vi.fn();
const fetchAdminOrderCounts = vi.fn();
const approveReturn = vi.fn();
const rejectReturn = vi.fn();
const shipOrder = vi.fn();
const deliverOrder = vi.fn();
const adminCancelOrder = vi.fn();

vi.mock('../api/order', async (importOriginal) => {
  // ⚠ 라벨·상태 맵은 **진짜를 쓴다** — 가짜로 갈아끼우면 이 화면이 그 맵을 쓴다는 사실이 검증에서 빠진다.
  const real = await importOriginal();
  return {
    ...real,
    fetchAdminOrders: (...a) => fetchAdminOrders(...a),
    fetchAdminOrderCounts: (...a) => fetchAdminOrderCounts(...a),
    approveReturn: (...a) => approveReturn(...a),
    rejectReturn: (...a) => rejectReturn(...a),
    shipOrder: (...a) => shipOrder(...a),
    deliverOrder: (...a) => deliverOrder(...a),
    adminCancelOrder: (...a) => adminCancelOrder(...a),
  };
});

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}));

import OrderAdminView from './OrderAdminView.vue';

function row(overrides = {}) {
  return {
    id: 'o1', orderNo: '20260825-0001', buyerNickname: 'ZZ구매자',
    status: 'RETURN_REQUESTED', totalPrice: 30_000, payAmount: 30_000,
    summary: 'ZZ상품 외 1건', itemCount: 2, createdAt: '2026-08-25T00:00:00Z',
    ...overrides,
  };
}

let w = null;

/**
 * 🔴 **그리드가 «행을 그릴 때까지» 기다린다 — 정해진 횟수만큼 flush 하지 않는다** (2026-08-27).
 *
 * 전엔 `await flushPromises()` **두 번**이었다. 그건 «두 번이면 되겠지» 라는 **추측**이고,
 * `CustomStore.load` → devextreme 내부 → Vue 렌더가 프로미스를 몇 개 거치는지는 우리 사정이 아니다.
 * ⚠ **단독으로 돌리면 늘 초록이라 안 보인다** — 전체 스위트로 돌려 부하가 걸리면 두 번이 모자라
 * 행이 아직 없고, `btn(w, '거절')` 이 `undefined` 가 되어 **«Cannot read properties of undefined»**
 * 로 죽는다. 🔴 **실패하는 테스트가 매번 달라서** 처음엔 그날 만진 코드 탓으로 보였다.
 *
 * 이 저장소의 두 번째 «가끔 빨개지는 테스트» 다(첫째는 `priceFilterUsesSalePrice`, 08-19 §7).
 * **다만 이쪽은 원인을 알고 고쳤다** — 구조를 바꿔 회피한 것이 아니다.
 */
async function untilRendered(w, tries = 50) {
  for (let i = 0; i < tries; i += 1) {
    // 행이 그려지면 처리 칸의 「상세」 버튼이 생긴다 — 상태와 무관하게 모든 행에 있는 유일한 버튼이다.
    if (btn(w, '상세')) return w;
    await flushPromises();
    await new Promise((resolve) => { setTimeout(resolve, 0); });
  }
  throw new Error('그리드가 행을 그리지 않았다 — 기다림을 포기했다');
}

async function open(rows = [row()]) {
  fetchAdminOrders.mockResolvedValue({ content: rows, totalElements: rows.length });
  fetchAdminOrderCounts.mockResolvedValue({ RETURN_REQUESTED: rows.length });
  w = mount(OrderAdminView);
  await flushPromises();
  return untilRendered(w);
}

const btn = (w, text) => w.findAll('button').find((b) => b.text() === text);

describe('OrderAdminView — 반품 승인·거절 (§I-2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  /**
   * ⚠ **마운트한 것은 반드시 언마운트한다.** DataGrid 가 뒤에서 계속 돌아 전수 실행에서만
   * `window.getComputedStyle is not a function` 이 처리되지 않은 에러로 터진다(08-14 실측).
   */
  afterEach(() => {
    w?.unmount();
    w = null;
    vi.restoreAllMocks();
  });

  it('🔴 그리드가 **실제로 그려진다** — 이 테스트의 전제를 먼저 못 박는다', async () => {
    const w = await open();
    expect(w.find('.dx-datagrid').exists()).toBe(true);
    expect(w.text()).toContain('20260825-0001');
  });

  // ── 🔴 거절: 2주간 항상 400 이던 자리 ─────────────────────────────

  it('🔴 「거절」은 confirm 이 아니라 **사유 패널**을 연다 — 사유 없이 보내지 않는다', async () => {
    const w = await open();

    await btn(w, '거절').trigger('click');
    await flushPromises();

    // 🔴 예전에는 여기서 곧바로 rejectReturn(id) 가 나갔고 서버가 400 을 돌려줬다.
    expect(rejectReturn).not.toHaveBeenCalled();
    expect(w.text()).toContain('반품 거절 — ZZ구매자님의 주문 (20260825-0001)');
    expect(w.text()).toContain('거절 사유');
  });

  it('🔴 사유를 비운 채 보내면 **막고 이유를 말한다** (서버 왕복 없이)', async () => {
    const w = await open();
    await btn(w, '거절').trigger('click');
    await flushPromises();

    await btn(w, '반품 거절').trigger('click');
    await flushPromises();

    expect(rejectReturn).not.toHaveBeenCalled();
    expect(w.text()).toContain('거절 사유를 입력해 주세요');
    // ⚠ **왜 필수인지**까지 말한다 — 거절은 상태를 안 남기므로 사유가 유일한 표시다(V47).
    expect(w.text()).toContain('유일한 표시');
  });

  it('🔴 사유를 적으면 **그 사유가 실려 나간다** — 이게 안 되면 서버가 400 을 준다', async () => {
    const w = await open();
    await btn(w, '거절').trigger('click');
    await flushPromises();

    await w.find('.card input.field').setValue('ZZ-사용 흔적이 있습니다');
    await btn(w, '반품 거절').trigger('click');
    await flushPromises();

    expect(rejectReturn).toHaveBeenCalledWith('o1', 'ZZ-사용 흔적이 있습니다');
  });

  // ── 승인 문구: 부분 반품이 생기며 거짓이 된 자리 ─────────────────

  it('🔴 승인 confirm 이 «결제금액» 이라고 말하지 않는다 — 부분 반품이면 거짓이다', async () => {
    const w = await open();

    await btn(w, '반품승인').trigger('click');
    await flushPromises();

    const asked = window.confirm.mock.calls[0][0];
    // 🔴 3개 중 1개만 요청된 주문에도 «결제금액을 환불» 이라고 말하던 자리다(전량 시절 문구).
    expect(asked).not.toContain('결제금액');
    expect(asked).toContain('요청된 품목');
    expect(approveReturn).toHaveBeenCalledWith('o1');
  });

  // ── 부분 수량이 목록에 보이는가 (2026-08-27, BACKLOG §I-7) ─────────────────

  it('🔴 승인 confirm 이 «몇 개 중 몇 개» 를 말한다 — 목록에서 승인하는 관리자가 모르고 누르던 자리', async () => {
    const w = await open([row({ totalQuantity: 3, returnRequestedQuantity: 1 })]);

    await btn(w, '반품승인').trigger('click');
    await flushPromises();

    const asked = window.confirm.mock.calls[0][0];
    expect(asked).toContain('3개 중 1개');
    expect(asked).toContain('요청된 품목');   // «무엇이» 를 말하는 말은 그대로 남는다
  });

  it('🔴 수량 칸이 없어도 «undefined개» 가 새지 않는다 — 옛 응답으로 되돌아가도 문구가 안 깨진다', async () => {
    // ⚠ 실제로 낸 실수다(2026-08-27). row() 기본값엔 부분 필드가 없다 — 그게 이 테스트의 조건이다.
    const w = await open();

    await btn(w, '반품승인').trigger('click');
    await flushPromises();

    expect(window.confirm.mock.calls[0][0]).not.toContain('undefined');
  });

  it('부분 반품 중인 주문과 멀쩡한 주문이 목록에서 갈린다', async () => {
    const w = await open([
      row({ id: 'o1', orderNo: 'ZZ-부분', status: 'DELIVERED',
            totalQuantity: 3, returnedQuantity: 1, remainingQuantity: 2 }),
      row({ id: 'o2', orderNo: 'ZZ-멀쩡', status: 'DELIVERED',
            totalQuantity: 3, returnedQuantity: 0, remainingQuantity: 3 }),
    ]);

    const text = w.text();
    // 🔴 고치기 전에는 두 줄이 **글자 그대로 같았다**(상태도 DELIVERED, 금액도 같다).
    expect(text).toContain('3개 중 1개');
    // 멀쩡한 쪽에는 흔적 줄이 아예 안 그려진다 — 「3개 중 0개」 같은 빈 말이 남으면 안 된다.
    expect(text).not.toContain('0개 반품됨');
  });

  it('«돌아올 것»(요청)과 «돌아온 것»(반품)이 섞이지 않는다', async () => {
    const w = await open([row({ totalQuantity: 3, returnRequestedQuantity: 1, returnedQuantity: 0 })]);

    expect(w.text()).toContain('1개 반품 요청됨');
    expect(w.text()).not.toContain('반품됨');   // 아직 안 빠졌다
  });
});

/* ═══════════ 여기부터 2026-09-01 — 08-25 가 「안 본다」고 적어 둔 나머지 ═══════════
 *
 * 🔴 **이 화면의 처리 버튼은 «상태에 따라» 나타났다 사라진다.** 그 조건이 서버 규칙과
 *    손으로 맞춰져 있다(취소 버튼 주석: *"서버의 isCancellable() 과 같은 조건이다"*).
 *    ⚠ **손으로 맞춘 짝은 한쪽만 움직이면 조용히 갈린다** — §I-2 가 그렇게 2주를 갔고,
 *    그때 갈린 방향은 «버튼은 있는데 서버가 400» 이었다. 그래서 **없어야 할 자리에 없는지**를
 *    있는지만큼 단언한다.
 */

const setup = () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });
  afterEach(() => {
    w?.unmount();
    w = null;
    vi.restoreAllMocks();
  });
};

describe('OrderAdminView — 발송 (운송장)', () => {
  setup();

  it('「발송」은 PAID 에만 뜬다 — 이미 보낸 주문에 또 뜨면 눌러 보고 400 을 받는다', async () => {
    const w = await open([row({ status: 'PAID' })]);
    expect(btn(w, '발송')).toBeTruthy();

    w.unmount();
    const w2 = await open([row({ status: 'SHIPPED' })]);
    expect(btn(w2, '발송')).toBeFalsy();
  });

  it('「발송」을 누르면 **어느 주문인지** 말하는 패널이 열린다 (그리드에서 행을 잃지 않게)', async () => {
    const w = await open([row({ status: 'PAID', buyerNickname: 'ZZ김구매' })]);
    await btn(w, '발송').trigger('click');
    await flushPromises();

    expect(w.text()).toContain('운송장 등록');
    expect(w.text()).toContain('ZZ김구매');
  });

  it('🔴 송장번호를 비운 채 보내면 **막고 이유를 말한다** — 서버 왕복 없이 (거절 사유와 같은 규칙)', async () => {
    const w = await open([row({ status: 'PAID' })]);
    await btn(w, '발송').trigger('click');
    await flushPromises();

    await btn(w, '발송 처리').trigger('click');
    await flushPromises();

    expect(shipOrder).not.toHaveBeenCalled();
    expect(w.text()).toContain('송장번호를 입력해 주세요');
  });

  it('송장번호를 적으면 **택배사와 함께** 실려 나간다', async () => {
    shipOrder.mockResolvedValue({});
    const w = await open([row({ status: 'PAID' })]);
    await btn(w, '발송').trigger('click');
    await flushPromises();

    await w.find('input[placeholder="숫자만 입력"]').setValue('  123456789012  ');
    await btn(w, '발송 처리').trigger('click');
    await flushPromises();

    // ⚠ 앞뒤 공백은 떼고 보낸다 — 복사·붙여넣기가 흔한 칸이다.
    expect(shipOrder).toHaveBeenCalledWith('o1', { carrier: 'CJ', trackingNo: '123456789012' });
  });
});

describe('OrderAdminView — 배송완료', () => {
  setup();

  it('「배송완료」는 SHIPPED 에만 뜬다', async () => {
    const w = await open([row({ status: 'SHIPPED' })]);
    expect(btn(w, '배송완료')).toBeTruthy();

    w.unmount();
    const w2 = await open([row({ status: 'PAID' })]);
    expect(btn(w2, '배송완료')).toBeFalsy();
  });

  it('⚠ confirm 을 취소하면 **안 부른다** — 되돌릴 수 없는 조작이다(적립이 나간다)', async () => {
    window.confirm.mockReturnValue(false);
    const w = await open([row({ status: 'SHIPPED' })]);

    await btn(w, '배송완료').trigger('click');
    await flushPromises();

    expect(deliverOrder).not.toHaveBeenCalled();
  });

  it('confirm 이 **누구의 주문인지** 말하고, 승인하면 그 주문을 넘긴다', async () => {
    deliverOrder.mockResolvedValue({});
    const w = await open([row({ status: 'SHIPPED', buyerNickname: 'ZZ김구매' })]);

    await btn(w, '배송완료').trigger('click');
    await flushPromises();

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('ZZ김구매'));
    expect(deliverOrder).toHaveBeenCalledWith('o1');
  });
});

describe('OrderAdminView — 관리자 대행 취소', () => {
  setup();

  it('🔴 「취소」는 발송 전(ORDERED·PAID)에만 뜬다 — 서버 isCancellable() 과 같은 조건이다', async () => {
    for (const status of ['ORDERED', 'PAID']) {
      const wOk = await open([row({ status })]);
      expect(btn(wOk, '취소'), `${status} 에는 있어야 한다`).toBeTruthy();
      wOk.unmount();
    }
    // 🔴 **없어야 하는 쪽이 이 테스트의 값이다** — 보이면 눌러 보고 400 을 받는데,
    //    그건 화면이 «될 것처럼» 보여 준 탓이다(§I-2 와 같은 방향의 사고).
    for (const status of ['SHIPPED', 'DELIVERED', 'RETURN_REQUESTED', 'CANCELLED']) {
      const wNo = await open([row({ status })]);
      expect(btn(wNo, '취소'), `${status} 에는 없어야 한다`).toBeFalsy();
      wNo.unmount();
    }
    w = null;
  });

  it('🔴 사유를 비운 채 보내면 막는다 — **관리자가 취소한 주문은 사유가 유일한 단서**다', async () => {
    const w = await open([row({ status: 'PAID' })]);
    await btn(w, '취소').trigger('click');
    await flushPromises();

    await btn(w, '취소 처리').trigger('click');
    await flushPromises();

    expect(adminCancelOrder).not.toHaveBeenCalled();
    expect(w.text()).toContain('취소 사유를 입력해 주세요');
  });

  it('사유를 적으면 **그 사유가 실려 나간다** (원장에 행위자와 함께 남는 값이다)', async () => {
    adminCancelOrder.mockResolvedValue({});
    const w = await open([row({ status: 'PAID' })]);
    await btn(w, '취소').trigger('click');
    await flushPromises();

    await w.find('input[placeholder="예) 고객 요청 — 전화 접수"]').setValue('고객 요청(전화)');
    await btn(w, '취소 처리').trigger('click');
    await flushPromises();

    expect(adminCancelOrder).toHaveBeenCalledWith('o1', '고객 요청(전화)');
  });
});

/**
 * 상태 탭을 잡는다.
 * ⚠ `btn(w, '발송완료')` 로는 못 잡는다 — **두 가지 이유**로 그렇다:
 *   ① 탭 버튼의 텍스트에는 **건수 배지가 붙어 있다**(「발송완료3」).
 *   ② 🔴 **처리 버튼과 이름이 겹친다** — 「배송완료」는 DELIVERED **탭**이기도 하고
 *      SHIPPED 행의 **처리 버튼**이기도 하다. 텍스트만으로 고르면 둘 중 아무거나 잡힌다.
 * → 탭에만 있는 `aria-current` 로 범위를 좁힌 뒤 **앞부분**으로 맞춘다.
 */
const tab = (w, text) => w.findAll('button[aria-current]').find((b) => b.text().startsWith(text));

/**
 * 🔴 **«다시 불렀다» 를 기다린다 — `untilRendered` 로는 안 된다.**
 * 그건 «행이 그려졌나» 를 보는데, 탭을 눌러도 **이전 행이 아직 그려져 있어서 즉시 돌아온다.**
 * ⚠ 08-27 이 «횟수를 기다림의 조건으로 바꾼다» 로 얻은 교훈의 **다른 얼굴**이다: 조건이 틀리면
 * 조건 대기도 소용없다. 여기서 기다릴 것은 렌더가 아니라 **호출**이다.
 */
async function untilCalledAgain(fn, tries = 50) {
  for (let i = 0; i < tries; i += 1) {
    if (fn.mock.calls.length > 0) return;
    await flushPromises();
    await new Promise((resolve) => { setTimeout(resolve, 0); });
  }
  throw new Error('목록을 다시 부르지 않았다 — 기다림을 포기했다');
}

/** DevExtreme 입력은 `change` 에서 값이 확정된다(AuditLogAdminView.test.js 실측). */
async function typeInto(w, placeholder, value) {
  const input = w.find(`input[placeholder="${placeholder}"]`);
  await input.setValue(value);
  await input.trigger('change');
  await flushPromises();
}

describe('OrderAdminView — 상태 탭 · 검색', () => {
  setup();

  it('탭을 누르면 **그 상태로** 다시 부른다', async () => {
    const w = await open([row({ status: 'PAID' })]);
    fetchAdminOrders.mockClear();

    await tab(w, '발송완료').trigger('click');
    await untilCalledAgain(fetchAdminOrders);

    expect(fetchAdminOrders).toHaveBeenCalledWith(expect.objectContaining({ status: 'SHIPPED' }));
  });

  it('「전체」 탭은 상태를 **비워서** 부른다 — 필터를 푸는 길이 있어야 한다', async () => {
    const w = await open([row()]);
    fetchAdminOrders.mockClear();

    await tab(w, '전체').trigger('click');
    await untilCalledAgain(fetchAdminOrders);

    expect(fetchAdminOrders).toHaveBeenCalledWith(expect.objectContaining({ status: null }));
  });

  it('구매자로 검색하면 그 조건이 실려 나가고, 「초기화」가 되돌린다', async () => {
    const w = await open([row()]);
    await typeInto(w, '닉네임', 'ZZ김구매');
    fetchAdminOrders.mockClear();

    await btn(w, '검색').trigger('click');
    await untilCalledAgain(fetchAdminOrders);
    expect(fetchAdminOrders).toHaveBeenCalledWith(expect.objectContaining({ buyer: 'ZZ김구매' }));

    fetchAdminOrders.mockClear();
    await btn(w, '초기화').trigger('click');
    await untilCalledAgain(fetchAdminOrders);
    expect(fetchAdminOrders).toHaveBeenCalledWith(expect.objectContaining({ buyer: '', status: null }));
  });
});
