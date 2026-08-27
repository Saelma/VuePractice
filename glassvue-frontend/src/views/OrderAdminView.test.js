import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 열 번째 화면 테스트 (2026-08-25, BACKLOG §I-2) — **반품 승인·거절 자리만** 본다.
//
// 🔴 **이 화면에 테스트가 없어서 버그가 2주를 갔다.** 거절 버튼이 사유 없이 `rejectReturn(row.id)` 를
//    불렀는데, 서버는 V47(2026-08-11)부터 `@NotBlank` 라 **항상 400** 이었다. 같은 날 주문 **상세**
//    에는 사유 폼이 붙었고 **목록만 안 열렸다** — WA §1-2-1 의 «짝 중 한쪽만 고쳐진다» 그대로다.
//
// ⚠ **화면 전체를 덮지 않는다.** 발송·배송완료·대행 취소·필터는 여기서 안 본다.
//    BACKLOG §I-8 이 이 화면의 나머지를 «다음» 으로 잡고 있고, 여기는 **오늘 고친 자리의 씨앗**이다.
//
// ⚠ DevExtreme 은 jsdom 에서 그대로 렌더된다(2026-08-14 실측, AuditLogAdminView.test.js 주석 참고).
//    `attachTo` 만 쓰지 않는다.

const fetchAdminOrders = vi.fn();
const fetchAdminOrderCounts = vi.fn();
const approveReturn = vi.fn();
const rejectReturn = vi.fn();

vi.mock('../api/order', async (importOriginal) => {
  // ⚠ 라벨·상태 맵은 **진짜를 쓴다** — 가짜로 갈아끼우면 이 화면이 그 맵을 쓴다는 사실이 검증에서 빠진다.
  const real = await importOriginal();
  return {
    ...real,
    fetchAdminOrders: (...a) => fetchAdminOrders(...a),
    fetchAdminOrderCounts: (...a) => fetchAdminOrderCounts(...a),
    approveReturn: (...a) => approveReturn(...a),
    rejectReturn: (...a) => rejectReturn(...a),
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

async function open(rows = [row()]) {
  fetchAdminOrders.mockResolvedValue({ content: rows, totalElements: rows.length });
  fetchAdminOrderCounts.mockResolvedValue({ RETURN_REQUESTED: rows.length });
  w = mount(OrderAdminView);
  await flushPromises();
  await flushPromises();
  return w;
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
