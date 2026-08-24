import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 일곱 번째 뷰 테스트 (2026-08-24, B-27). 패턴은 `ProductDiscountAdminView.test.js` 에서 가져왔다.
//
// 🔴 **이 화면을 고른 이유는 여기가 「보이는 것」이 곧 결론인 자리이기 때문이다.** 다른 화면은
//    틀리면 값이 틀리는데, 달력은 **틀려도 값은 맞다** — 격자가 멀쩡히 그려지고 막대도 다 있는데
//    **순서만 뒤집혀** 관리자가 겹침을 반대로 읽는다. 그리고 그건 아무 에러도 안 낸다.
//
// 🔴 **`KIND_ORDER` 를 아무것도 안 지키고 있었다**(08-19 이월이 사흘째 지목한 자리). 그 상수는
//    *「겹치면 안 되는 것을 위로」* 라는 규칙을 코드로 적은 것인데, 규칙을 **되돌려 놔도**
//    화면은 똑같이 돌고 전수도 초록이었다. 아래 두 개가 그 자리를 막는다.
//
// ⚠ **이 테스트가 «겹침이 사고인가» 를 판단하지는 못한다** — 그건 사람이 읽는 것이다. 여기서
//    고정하는 것은 **읽는 순서가 규칙대로 놓이는가** 뿐이다. 안 밟은 것을 밟았다고 적지 않는다.

const fetchPromotionCalendar = vi.fn();

vi.mock('../api/coupon', () => ({
  fetchPromotionCalendar: (...a) => fetchPromotionCalendar(...a),
}));

import PromotionCalendarAdminView from './PromotionCalendarAdminView.vue';

/**
 * 서버가 주는 막대 하나(`PromotionSpanResponse`).
 *
 * ⚠ **`id` 다. `couponId` 가 아니다** — 08-19(G-5)에 상품 세일이 같은 격자에 올라오면서
 *    중립화된 이름이다. 고객 배너 DTO(`EventCouponResponse`)는 아직 `couponId` 라
 *    **저장소에서 이름을 세면 살아 있는 것처럼 보인다**(WA §2-10 이 잡으려던 함정).
 *
 * 🔴 **이 테스트는 그 죽은 이름을 못 잡는다 — 못 잡는 이유를 적어 둔다.** 상시 스트립은
 *    `v-if="!loading && ..."` 안에 있고 `load()` 가 맨 먼저 `loading = true` 를 놓는다.
 *    즉 달을 옮길 때마다 스트립이 **통째로 헐렸다 다시 선다** — `v-for` 키가 끼어들 패치가
 *    아예 없다. 2026-08-24 에 «키 정체» 로 잡아 보려다 실측(`first.isConnected === false`)으로
 *    기각했다. **테스트가 통과했지만 이유가 달라서 지웠다.**
 *    ⚠ 그래서 이름은 고치되(죽은 이름이다) **오늘 눈에 보이는 고장은 없었다.**
 */
function span(overrides = {}) {
  return {
    id: 's1', name: 'ZZ-쿠폰', label: '20% 할인', kind: 'USE',
    startDay: 3, endDay: 5,
    continuesBefore: false, continuesAfter: false,
    welcome: false, gridded: true,
    ...overrides,
  };
}

/**
 * 2026년 8월. **1일이 토요일**이라 `firstDayOfWeek = 6` 이고, 첫 주는 앞 다섯 칸이 빈다.
 * 🔴 **일부러 «1일이 월요일이 아닌» 달을 골랐다** — 월요일 달이면 빈칸 계산이 전부 0이라
 *    격자가 밀려도 아무것도 안 걸린다.
 */
function calendar(spans = [], overrides = {}) {
  return { month: '2026-08', daysInMonth: 31, firstDayOfWeek: 6, spans, ...overrides };
}

describe('PromotionCalendarAdminView', () => {
  beforeEach(() => {
    fetchPromotionCalendar.mockReset().mockResolvedValue(calendar());
  });

  async function mountView() {
    const w = mount(PromotionCalendarAdminView, {
      global: { stubs: { RouterLink: true } },
    });
    await flushPromises();
    return w;
  }

  /** 막대만 고른다 — 격자에서 `title` 을 가진 것은 막대뿐이다. */
  const bars = (w) => w.findAll('[title]');
  /** 주 한 줄. ⚠ 요일 머리는 `pb-1` 이라 안 걸린다(막대 줄은 `py-1`). */
  const weeks = (w) => w.findAll('div.border-b.border-line.py-1');

  // ── 🔴 순서 — 이 화면이 뜻을 만드는 자리 ────────────────────

  it('🔴 같은 날 셋이 겹치면 **발급 창 → 타임세일 → 사용 기간** 순으로 쌓인다', async () => {
    // ⚠ 일부러 규칙과 **반대로** 넣는다 — 서버가 준 순서를 그대로 그리면 여기서 걸린다.
    fetchPromotionCalendar.mockResolvedValue(calendar([
      span({ id: 'u', kind: 'USE', name: 'ZZ-사용', startDay: 3, endDay: 9 }),
      span({ id: 's', kind: 'SALE', name: 'ZZ-세일', startDay: 3, endDay: 9 }),
      span({ id: 'i', kind: 'ISSUE', name: 'ZZ-발급', startDay: 3, endDay: 9 }),
    ]));
    const w = await mountView();

    expect(bars(w).map((b) => b.attributes('title').split(' · ').pop()))
      .toEqual(['발급 창', '타임세일', '사용 기간']);
  });

  it('🔴 같은 종류면 **시작이 이른 것부터** — 안 그러면 새로고침마다 줄 순서가 바뀐다', async () => {
    fetchPromotionCalendar.mockResolvedValue(calendar([
      span({ id: 'b', kind: 'SALE', name: 'ZZ-늦은세일', startDay: 7, endDay: 9 }),
      span({ id: 'a', kind: 'SALE', name: 'ZZ-이른세일', startDay: 3, endDay: 5 }),
    ]));
    const w = await mountView();

    expect(bars(w).map((b) => b.attributes('title').split(' · ')[0]))
      .toEqual(['ZZ-이른세일', 'ZZ-늦은세일']);
  });

  it('막대에 **이름과 할인율이 함께** 실린다 — 겹침은 「무엇이 얼마나」까지 읽혀야 뜻이 된다', async () => {
    fetchPromotionCalendar.mockResolvedValue(calendar([
      span({ kind: 'SALE', name: 'ZZ-세일검증', label: '20% 할인' }),
    ]));
    const w = await mountView();

    expect(bars(w)[0].text()).toContain('ZZ-세일검증');
    expect(bars(w)[0].text()).toContain('20% 할인');
  });

  // ── 격자 — 날짜가 어느 칸에 앉나 ────────────────────────────

  it('🔴 1일이 토요일이면 첫 주의 **앞 다섯 칸이 빈다**', async () => {
    const w = await mountView();
    const firstWeekDays = weeks(w)[0].findAll('div.grid.grid-cols-7 > div');

    expect(firstWeekDays).toHaveLength(7);
    expect(firstWeekDays.slice(0, 5).every((d) => d.classes('text-transparent'))).toBe(true);
    expect(firstWeekDays[5].text()).toBe('1');
    expect(firstWeekDays[6].text()).toBe('2');
  });

  it('🔴 막대가 **그 달 1일의 요일에 맞춰** 앉는다 — 3일(월)은 둘째 주 첫 칸이다', async () => {
    fetchPromotionCalendar.mockResolvedValue(calendar([span({ startDay: 3, endDay: 5 })]));
    const w = await mountView();

    // 둘째 주는 3~9일. 3일이 첫 칸(col 1)이고 3일이라 3칸(3·4·5)을 먹는다.
    expect(bars(w)[0].element.parentElement.style.gridColumn).toBe('1 / span 3');
  });

  it('그 주에 안 걸치는 막대는 그 주에 안 나온다', async () => {
    fetchPromotionCalendar.mockResolvedValue(calendar([span({ startDay: 20, endDay: 22 })]));
    const w = await mountView();

    // 20~22일은 넷째 주(17~23)에만 있다.
    expect(bars(w)).toHaveLength(1);
    expect(weeks(w)[3].findAll('[title]')).toHaveLength(1);
    expect(weeks(w)[1].findAll('[title]')).toHaveLength(0);
  });

  // ── 🔴 잘린 막대 — 여기서 시작·끝난 것처럼 보이면 거짓말이다 ──

  it('🔴 주를 넘는 막대는 **양쪽 주에 잘려** 나오고, 잘린 쪽 모서리가 열린다', async () => {
    fetchPromotionCalendar.mockResolvedValue(calendar([span({ startDay: 7, endDay: 12 })]));
    const w = await mountView();

    const [left, right] = bars(w);
    expect(bars(w)).toHaveLength(2);

    // 둘째 주(3~9): 7~9일 → 5번째 칸부터 3칸. 오른쪽이 잘렸다.
    expect(left.element.parentElement.style.gridColumn).toBe('5 / span 3');
    expect(left.classes()).toContain('rounded-r-none');
    expect(left.classes()).not.toContain('rounded-l-none');

    // 셋째 주(10~16): 10~12일 → 첫 칸부터 3칸. 왼쪽이 잘렸다.
    expect(right.element.parentElement.style.gridColumn).toBe('1 / span 3');
    expect(right.classes()).toContain('rounded-l-none');
    expect(right.classes()).not.toContain('rounded-r-none');
  });

  it('🔴 **지난달부터 이어져 온** 막대는 첫 칸이어도 왼쪽이 열려 있다', async () => {
    fetchPromotionCalendar.mockResolvedValue(calendar([
      span({ startDay: 1, endDay: 2, continuesBefore: true }),
    ]));
    const w = await mountView();

    expect(bars(w)[0].classes()).toContain('rounded-l-none');
    expect(bars(w)[0].text()).toContain('‹');
  });

  it('🔴 **다음 달로 이어지는** 막대는 마지막 칸이어도 오른쪽이 열려 있다', async () => {
    fetchPromotionCalendar.mockResolvedValue(calendar([
      span({ startDay: 30, endDay: 31, continuesAfter: true }),
    ]));
    const w = await mountView();

    const last = bars(w)[bars(w).length - 1];
    expect(last.classes()).toContain('rounded-r-none');
    expect(last.text()).toContain('›');
  });

  // ── 상시 쿠폰 — 격자 밖 스트립 ──────────────────────────────

  it('🔴 상시 쿠폰(`gridded=false`)은 **격자에 안 그린다** — 가로줄이 이벤트 겹침을 덮는다', async () => {
    fetchPromotionCalendar.mockResolvedValue(calendar([
      span({ id: 'always', name: 'ZZ-상시', gridded: false, startDay: 1, endDay: 31 }),
      span({ id: 'event', name: 'ZZ-이벤트', kind: 'ISSUE', startDay: 3, endDay: 5 }),
    ]));
    const w = await mountView();

    expect(bars(w)).toHaveLength(1);
    expect(bars(w)[0].attributes('title')).toContain('ZZ-이벤트');
    // 격자에서 뺐다고 **없애지는 않는다** — 스트립에 남는다.
    expect(w.text()).toContain('이 달 내내 도는 상시 쿠폰');
    expect(w.text()).toContain('ZZ-상시');
  });

  it('🔴 이 달에 **끝나는** 상시 쿠폰은 종료일을 적는다 — 상시에서 유일하게 날짜가 뜻을 가진다', async () => {
    fetchPromotionCalendar.mockResolvedValue(calendar([
      span({ name: 'ZZ-상시', gridded: false, startDay: 1, endDay: 20, continuesAfter: false }),
    ]));
    const w = await mountView();

    expect(w.text()).toContain('20일 종료');
  });

  it('다음 달로 이어지는 상시 쿠폰에는 종료일을 안 적는다 — 적을 날이 없다', async () => {
    fetchPromotionCalendar.mockResolvedValue(calendar([
      span({ name: 'ZZ-상시', gridded: false, startDay: 1, endDay: 31, continuesAfter: true }),
    ]));
    const w = await mountView();

    expect(w.text()).not.toContain('종료');
  });

  it('상시 쿠폰이 여럿이면 줄이 각각 선다 — 순서는 서버가 준 그대로다', async () => {
    fetchPromotionCalendar.mockResolvedValue(calendar([
      span({ id: 'a', name: 'ZZ-상시1', gridded: false, continuesAfter: true }),
      span({ id: 'b', name: 'ZZ-상시2', gridded: false, continuesAfter: true }),
      span({ id: 'c', name: 'ZZ-상시3', gridded: false, continuesAfter: true }),
    ]));
    const w = await mountView();

    const items = w.findAll('ul li');
    expect(items).toHaveLength(3);
    expect(items.map((li) => li.text().split(' ')[0]))
      .toEqual(['ZZ-상시1', 'ZZ-상시2', 'ZZ-상시3']);
  });

  // ── 빈 달 · 실패 ────────────────────────────────────────────

  it('막대가 하나도 없는 달은 **빈 달력이 정상**이다 — 이벤트가 매달 있지 않다', async () => {
    const w = await mountView();
    expect(w.text()).toContain('이 달에는 예정된 이벤트도 타임세일도 없어요');
  });

  it('🔴 상시 쿠폰만 있는 달도 격자는 비지만, **「쿠폰이 없다」로 말하지 않는다**', async () => {
    fetchPromotionCalendar.mockResolvedValue(calendar([
      span({ name: 'ZZ-상시', gridded: false, continuesAfter: true }),
    ]));
    const w = await mountView();

    // 격자 기준으로는 비어서 안내가 뜨는 게 맞다 — 다만 그 문구가 스트립과 어긋나면 안 된다.
    expect(w.text()).toContain('예정된 이벤트도 타임세일도 없어요');
    expect(w.text()).toContain('ZZ-상시');
  });

  it('🔴 불러오기 실패를 **빈 달력으로 위장하지 않는다** — 빈 격자는 「프로모션이 없다」로 읽힌다', async () => {
    fetchPromotionCalendar.mockRejectedValue(new Error('서버 오류'));
    const w = await mountView();

    expect(w.text()).toContain('서버 오류');
    expect(w.text()).not.toContain('예정된 이벤트도 타임세일도 없어요');
    expect(weeks(w)).toHaveLength(0);
  });

  // ── 달 이동 ────────────────────────────────────────────────

  it('🔴 첫 로드는 달을 **안 보낸다** — 화면이 「오늘」을 정하지 않는다(시간대가 갈린다)', async () => {
    await mountView();
    expect(fetchPromotionCalendar).toHaveBeenCalledWith(undefined);
  });

  it('서버가 정한 달을 화면이 받아 적는다', async () => {
    const w = await mountView();
    expect(w.text()).toContain('2026년 8월');
  });

  it('이전 달 · 다음 달로 옮긴다', async () => {
    const w = await mountView();

    // ⚠ **다음 응답을 먼저 세워 둔다** — 화면은 돌아온 `month` 를 자기 상태로 받아 적으므로
    //    (아래 테스트가 그 자리를 따로 잡는다) 목이 낡으면 **달이 되돌아온다.**
    fetchPromotionCalendar.mockResolvedValue(calendar([], { month: '2026-07', firstDayOfWeek: 3 }));
    await w.findAll('button').find((b) => b.text().includes('이전 달')).trigger('click');
    expect(fetchPromotionCalendar).toHaveBeenLastCalledWith('2026-07');
    await flushPromises();

    fetchPromotionCalendar.mockResolvedValue(calendar([], { month: '2026-08' }));
    await w.findAll('button').find((b) => b.text().includes('다음 달')).trigger('click');
    expect(fetchPromotionCalendar).toHaveBeenLastCalledWith('2026-08');
  });

  it('🔴 **서버가 돌려준 달이 화면 상태를 이긴다** — 그래야 다음 이동이 서버 기준에서 출발한다', async () => {
    const w = await mountView();

    // 서버가 «7월» 을 청했는데 «9월» 로 답하는 경우다(달을 안 보낸 첫 로드가 실제로 그렇다).
    fetchPromotionCalendar.mockResolvedValue(calendar([], { month: '2026-09', firstDayOfWeek: 2 }));
    await w.findAll('button').find((b) => b.text().includes('이전 달')).trigger('click');
    await flushPromises();

    expect(w.text()).toContain('2026년 9월');
    // 다음 이동은 화면이 청한 7월이 아니라 **서버가 답한 9월** 에서 출발한다.
    await w.findAll('button').find((b) => b.text().includes('다음 달')).trigger('click');
    expect(fetchPromotionCalendar).toHaveBeenLastCalledWith('2026-10');
  });

  it('🔴 **연 경계를 넘는다** — 1월의 이전 달은 작년 12월이다', async () => {
    fetchPromotionCalendar.mockResolvedValue(calendar([], { month: '2026-01', firstDayOfWeek: 4 }));
    const w = await mountView();

    await w.findAll('button').find((b) => b.text().includes('이전 달')).trigger('click');
    expect(fetchPromotionCalendar).toHaveBeenLastCalledWith('2025-12');
  });
});
