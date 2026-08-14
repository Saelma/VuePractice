import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 두 번째 뷰 테스트 (2026-08-14). 첫 사례는 `CouponAdminView.test.js` 이고 패턴은 거기서 가져왔다.
//
// 🔴 **이 화면을 고른 이유는 오늘 여기서 검증 지시가 틀렸기 때문이다.** F-7 의 멱등 가드를
//    *"복구를 한 번 더 눌러 보라"* 로 안내했는데 **그 버튼이 없다** — 복구하면 `load()` 가 목록을
//    다시 받고 그 줄이 곧바로 사라진다. 즉 **가드가 막는 것은 «두 번 누르기» 가 아니라 «낡은 목록»**
//    이고, 그 사실의 근거가 되는 화면 동작을 **아무 테스트도 지키고 있지 않았다.**
//    여기를 고정해 두면 다음 사람이 그 안내를 다시 쓰지 않는다.

const fetchDeletedProducts = vi.fn();
const restoreProduct = vi.fn();
vi.mock('../api/product', () => ({
  fetchDeletedProducts: (...a) => fetchDeletedProducts(...a),
  restoreProduct: (...a) => restoreProduct(...a),
}));

import ProductTrashAdminView from './ProductTrashAdminView.vue';

const DAY = 86_400_000;
const NOW = new Date('2026-08-14T11:00:00+09:00');

/** 서버가 주는 줄 하나. ⚠ `purgeAt` 은 **서버가 계산해 준다**(화면이 deletedAt + 7일 을 더하지 않는다). */
function row(overrides = {}) {
  return {
    id: 'p1', name: 'ZZ-지운상품', categoryName: '키보드',
    deletedAt: NOW.toISOString(), deletedBy: 'ZZ관리자',
    purgeAt: new Date(NOW.getTime() + 7 * DAY).toISOString(),
    ...overrides,
  };
}

describe('ProductTrashAdminView', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    vi.stubGlobal('confirm', vi.fn(() => true));
    fetchDeletedProducts.mockReset();
    restoreProduct.mockReset().mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  async function mountWith(...responses) {
    responses.forEach((r) => fetchDeletedProducts.mockResolvedValueOnce(r));
    const w = mount(ProductTrashAdminView);
    await flushPromises();
    return w;
  }

  // ── 복구하면 그 줄이 사라진다 (오늘 드러난 자리) ──────────────

  it('🔴 복구하면 **그 줄이 목록에서 곧바로 빠진다** — 그래서 「한 번 더 누르기」는 화면에 없다', async () => {
    const w = await mountWith([row()], []); // 두 번째 응답 = 복구 뒤 목록(비었다)
    expect(w.text()).toContain('ZZ-지운상품');

    await w.find('button.btn-secondary').trigger('click');
    await flushPromises();

    expect(restoreProduct).toHaveBeenCalledWith('p1');
    // 🔴 여기가 요점이다: 복구 뒤 목록을 **다시 받는다**. 그래서 두 번째 복구 버튼이 존재하지 않는다.
    expect(fetchDeletedProducts).toHaveBeenCalledTimes(2);
    expect(w.text()).not.toContain('ZZ-지운상품');
    expect(w.findAll('button.btn-secondary')).toHaveLength(0);
  });

  it('확인 대화를 취소하면 **아무 일도 안 일어난다** — 목록도 다시 받지 않는다', async () => {
    vi.stubGlobal('confirm', vi.fn(() => false));
    const w = await mountWith([row()]);

    await w.find('button.btn-secondary').trigger('click');
    await flushPromises();

    expect(restoreProduct).not.toHaveBeenCalled();
    expect(fetchDeletedProducts).toHaveBeenCalledTimes(1);
    expect(w.text()).toContain('ZZ-지운상품');
  });

  // ── 남은 기간 — 이 화면의 요점 ────────────────────────────────

  it('D-day 를 서버가 준 purgeAt 으로 센다 (7일 남으면 D-7, 급하지 않다)', async () => {
    const w = await mountWith([row()]);

    const badge = w.find('.badge');
    expect(badge.text()).toBe('D-7');
    expect(badge.classes()).toContain('badge-neutral');
  });

  it('🔴 하루 이하로 남으면 **급한 것으로 표시한다** — 지나면 되돌릴 수 없다', async () => {
    const w = await mountWith([row({ purgeAt: new Date(NOW.getTime() + DAY / 2).toISOString() })]);

    const badge = w.find('.badge');
    expect(badge.text()).toBe('D-1'); // 반나절도 올림해서 D-1 — 「D-0」은 읽는 사람마다 뜻이 갈린다
    expect(badge.classes()).toContain('badge-danger');
  });

  it('이미 지난 것은 **「곧 사라짐」** 이다 (음수 D-day 를 그리지 않는다)', async () => {
    const w = await mountWith([row({ purgeAt: new Date(NOW.getTime() - 1000).toISOString() })]);

    expect(w.find('.badge').text()).toBe('곧 사라짐');
    expect(w.find('.badge').classes()).toContain('badge-danger');
  });

  // ── 실패를 빈 목록으로 위장하지 않는다 ─────────────────────────

  it('🔴 목록을 못 불러오면 **말한다** — 0건으로 그리면 「되돌릴 것이 없다」로 읽힌다', async () => {
    fetchDeletedProducts.mockRejectedValueOnce(new Error('서버가 응답하지 않습니다'));
    const w = mount(ProductTrashAdminView);
    await flushPromises();

    expect(w.text()).toContain('목록을 불러오지 못했습니다');
    expect(w.text()).toContain('서버가 응답하지 않습니다');
  });
});
