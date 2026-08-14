import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 세 번째 화면 테스트 (2026-08-14). 패턴은 `views/CouponAdminView.test.js` 에서 가져왔다.
//
// 🔴 **이 배너를 고른 이유는 「지킬 것」이 이미 글로 적혀 있기 때문이다.** DESIGN §6-1 과 이 파일의
//    머리 주석에 다섯 가지가 번호까지 붙어 있는데(①연출은 기다림을 설명한다 ②실패에도 모션이 있다
//    ③연타를 막는다 ④reduce-motion 이면 즉시 ⑤강조색은 CTA·상태에만), **그 중 어느 것도 테스트가
//    없었다.** 글로 적힌 규칙은 곧 명세다 — 옮기기만 하면 된다.
//
// ⚠ 그리고 **2026-08-14 아침에 운영에서 실제로 본 전이**가 여기다(D-1 예고 → 「받기」 →
//    받은 뒤 「다음 이벤트」 줄). 그때 눈으로 본 것을 여기에 고정한다.
//
// ⚠ `components/` 라 엄밀히는 「뷰 테스트」가 아니다. 그래도 이걸 먼저 한 이유는 값이 크고,
//    남은 관리자 화면들은 **DevExtreme 을 쓰므로 별도 판단**이 필요하기 때문이다(핸드오프 §10-1 ③).

const fetchEventCoupon = vi.fn();
const claimEventCoupon = vi.fn();
vi.mock('../api/coupon', () => ({
  fetchEventCoupon: (...a) => fetchEventCoupon(...a),
  claimEventCoupon: (...a) => claimEventCoupon(...a),
  couponDiscountText: (c) => `${c.discountValue}원 할인`,
}));
vi.mock('../api/product', () => ({ priceText: (n) => `${n}원` }));

import EventCouponBanner from './EventCouponBanner.vue';

/** 서버가 주는 배너. 오늘 아침 운영 응답(`GET /api/coupons/event`)과 같은 모양이다. */
function banner(overrides = {}) {
  return {
    couponId: 'c1', name: 'ZZ-이벤트쿠폰3', discountType: 'FIXED', discountValue: 6000,
    minOrderAmount: 0, maxDiscountAmount: null,
    issueUntil: '2026-08-14T14:59:59Z', validUntil: '2026-09-13T14:59:59Z',
    validFrom: '2026-08-14T15:00:00Z',
    open: true, claimed: false, daysUntil: null, moreUpcoming: 0, nextDaysUntil: null,
    ...overrides,
  };
}

async function mountWith(data) {
  fetchEventCoupon.mockResolvedValueOnce(data);
  const w = mount(EventCouponBanner);
  await flushPromises();
  return w;
}

describe('EventCouponBanner', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    fetchEventCoupon.mockReset();
    claimEventCoupon.mockReset().mockResolvedValue(undefined);
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  // ── 무엇을 말하는가 — 배너는 둘 중 하나만 말한다 ───────────────

  it('줄 게 없으면 **자리를 만들지 않는다**(빈 상태 문구도 없다)', async () => {
    const w = await mountWith(null);
    expect(w.find('section').exists()).toBe(false);
  });

  it('🔴 배너 조회가 실패해도 **홈을 망가뜨리지 않는다** — 자리를 안 만들 뿐이다', async () => {
    fetchEventCoupon.mockRejectedValueOnce(new Error('500'));
    const w = mount(EventCouponBanner);
    await flushPromises();

    expect(w.find('section').exists()).toBe(false); // 던지지 않고 조용히 접힌다
  });

  it('🔴 예고에는 **버튼이 없다** — 행동을 요구하지 않는다', async () => {
    const w = await mountWith(banner({ open: false, daysUntil: 3 }));

    expect(w.text()).toContain('다음 이벤트');
    expect(w.text()).toContain('D-3');
    expect(w.find('button').exists()).toBe(false); // 누를 것이 없다
  });

  it('예고는 앞으로 더 있으면 **개수만** 말한다(목록으로 늘어놓지 않는다)', async () => {
    const w = await mountWith(banner({ open: false, daysUntil: 3, moreUpcoming: 2 }));

    expect(w.text()).toContain('2개');
    expect(w.text()).not.toContain('ZZ-이벤트쿠폰3 ·'); // 이름을 나열하지 않는다
  });

  it('오늘 열려 있으면 「쿠폰 받기」다', async () => {
    const w = await mountWith(banner());

    const btn = w.find('button');
    expect(btn.text()).toContain('쿠폰 받기');
    expect(btn.attributes('disabled')).toBeUndefined();
  });

  // ── 오늘 아침 운영에서 본 자리 ────────────────────────────────

  it('🔴 **받기 전에는 「다음 이벤트」를 말하지 않는다** — 행동 두 개를 동시에 요구하지 않는다', async () => {
    const w = await mountWith(banner({ nextDaysUntil: 5 }));

    expect(w.text()).toContain('쿠폰 받기');
    expect(w.text()).not.toContain('다음 이벤트'); // 아직 안 받았다
  });

  it('받고 나면 그 자리에 **「다음 이벤트」가 들어선다**', async () => {
    const w = await mountWith(banner({ claimed: true, nextDaysUntil: 5 }));

    expect(w.text()).toContain('쿠폰함에 담겼어요');
    expect(w.text()).toContain('다음 이벤트');
    expect(w.text()).toContain('D-5');
  });

  it('🔴 뒤에 남은 이벤트가 없으면 **그 줄이 사라진다** — 2026-08-14 아침 운영 실측이 이 상태다', async () => {
    // 그날 응답: moreUpcoming 0 · nextDaysUntil null (마지막 이벤트였다)
    const w = await mountWith(banner({ claimed: true, nextDaysUntil: null, moreUpcoming: 0 }));

    expect(w.text()).toContain('쿠폰함에 담겼어요');
    expect(w.text()).not.toContain('다음 이벤트');
    expect(w.find('button').attributes('disabled')).toBeDefined(); // 받음 = 더 누를 것이 없다
  });

  // ── DESIGN §6-1 의 「지킬 것」 ────────────────────────────────

  it('③ **연타를 막는다** — 같은 틱에 세 번 눌러도 발급은 한 번뿐이다', async () => {
    let resolveClaim;
    claimEventCoupon.mockReturnValueOnce(new Promise((r) => { resolveClaim = r; }));
    const w = await mountWith(banner());

    // 🔴 **await 를 걸지 않고** 연달아 누른다. 이것이 JS 가드(`claim()` 의 이른 return)가
    //    실제로 필요한 경로다 — Vue 는 비동기로 렌더하므로 `:disabled` 가 DOM 에 붙기 **전에**
    //    두 번째 클릭이 도착한다(실제 브라우저의 빠른 연타가 이 모양이다).
    // ⚠ await 를 걸면 첫 클릭 뒤 `disabled` 가 붙어 **두 번째 클릭이 아예 안 나가고**,
    //    그러면 이 테스트는 JS 가드가 아니라 `disabled` 속성을 보게 된다 —
    //    실제로 처음에 그렇게 썼다가 **가드를 지워도 초록**인 것을 변형 주입에서 잡았다(WA §2-4-1).
    const btn = w.find('button');
    btn.trigger('click');
    btn.trigger('click');
    btn.trigger('click');
    await flushPromises();

    expect(claimEventCoupon).toHaveBeenCalledTimes(1);
    expect(w.find('button').text()).toContain('받는 중');
    expect(w.find('button').attributes('disabled')).toBeDefined();

    resolveClaim();
    await vi.advanceTimersByTimeAsync(400); // ① 「기대감」 몫
    await flushPromises();
    expect(w.find('button').text()).toContain('받음');
  });

  it('① 응답이 즉시 와도 **최소 400ms 는 연출을 보여준다**(기다림을 설명한다)', async () => {
    const w = await mountWith(banner());

    await w.find('button').trigger('click');
    await flushPromises(); // 서버는 이미 답했다

    expect(w.find('button').text()).toContain('받는 중'); // 아직 확정하지 않는다

    await vi.advanceTimersByTimeAsync(400);
    expect(w.find('button').text()).toContain('받음');
  });

  it('④ `prefers-reduced-motion` 이면 **즉시 전환**한다(연출을 건너뛴다)', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    const w = await mountWith(banner());

    await w.find('button').trigger('click');
    await flushPromises(); // 타이머를 전혀 안 돌린다

    expect(w.find('button').text()).toContain('받음');
  });

  it('🔴 「이미 받음」(409)은 **실패가 아니라 상태 확정**이다 — 다른 탭에서 받았을 때 이 답이 온다', async () => {
    claimEventCoupon.mockRejectedValueOnce(Object.assign(new Error('이미 받았습니다'), { code: 'COUPON-409I' }));
    const w = await mountWith(banner());

    await w.find('button').trigger('click');
    await vi.advanceTimersByTimeAsync(400);
    await flushPromises();

    expect(w.find('button').text()).toContain('받음');
    expect(w.find('[role="status"]').exists()).toBe(false); // 에러로 말하지 않는다
  });

  it('② **실패에도 말이 있다** — 이유를 띄우고 잠시 뒤 다시 누를 수 있게 돌아온다', async () => {
    claimEventCoupon.mockRejectedValueOnce(Object.assign(new Error('잠시 뒤 다시 시도해 주세요'), { code: 'X' }));
    const w = await mountWith(banner());

    await w.find('button').trigger('click');
    await vi.advanceTimersByTimeAsync(400);
    await flushPromises();

    expect(w.find('[role="status"]').text()).toContain('잠시 뒤 다시 시도해 주세요');

    // 멈춘 채로 두지 않는다 — 되돌아와서 다시 시도할 수 있어야 한다.
    await vi.advanceTimersByTimeAsync(2400);
    await flushPromises();
    expect(w.find('button').text()).toContain('쿠폰 받기');
    expect(w.find('[role="status"]').exists()).toBe(false);
  });

  it('🔴 발급 창이 닫혔으면(400C) **배너를 다시 읽는다** — 화면이 낡은 것이다', async () => {
    claimEventCoupon.mockRejectedValueOnce(Object.assign(new Error('마감됐어요'), { code: 'COUPON-400C' }));
    const w = await mountWith(banner());
    // ⚠ 두 번째 응답은 **mount 뒤에** 큐에 넣는다 — 먼저 넣으면 onMounted 가 그걸 집어
    //    배너가 아예 안 뜨고, 그러면 이 테스트는 「버튼이 없다」로 죽는다(처음에 그렇게 틀렸다).
    fetchEventCoupon.mockResolvedValueOnce(null); // 다시 읽으면 이제 줄 게 없다

    await w.find('button').trigger('click');
    await vi.advanceTimersByTimeAsync(400);
    await flushPromises();

    expect(fetchEventCoupon).toHaveBeenCalledTimes(2);
    expect(w.find('section').exists()).toBe(false); // 낡은 「받기」를 계속 보여주지 않는다
  });
});
