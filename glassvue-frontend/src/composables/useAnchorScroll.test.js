import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { useAnchorScroll } from './useAnchorScroll';

/**
 * 앵커 스크롤 (WORKING-AGREEMENTS §2-9, 2026-08-07 에 공용으로 뽑음).
 *
 * 🔴 **여기서 지키는 것은 «한 번 계산해서 끝내지 않는다» 다.** 위쪽 섹션이 데이터를 나중에 받아
 * 렌더되면 목표가 아래로 밀리는데, 스크롤은 시작할 때 계산한 위치에서 멈춘다.
 * 그래서 «엉뚱한 데 서 있는» 상태가 되는데 — ⚠ **에러도 로그도 안 남는다.** 화면은 멀쩡히 그려지고
 * 스크롤도 «되긴» 했다. 2026-07-31 에 이 자리를 실제로 잃었고 아무도 못 봤다.
 */
describe('useAnchorScroll', () => {
  let ro;
  let el;

  beforeEach(() => {
    // jsdom 에 ResizeObserver 가 없다 — 관측 콜백을 직접 잡아 «다시 맞추는지» 를 본다.
    ro = { callback: null, observe: vi.fn(), disconnect: vi.fn() };
    // ⚠ 화살표 함수로 만들면 `new ResizeObserver(...)` 가 «is not a constructor» 로 터진다.
    //    일반 함수라야 `new` 가 되고, 그때 this 가 새 인스턴스라 메서드를 거기 붙여야 한다.
    global.ResizeObserver = vi.fn(function ResizeObserverMock(cb) {
      ro.callback = cb;
      this.observe = ro.observe;
      this.disconnect = ro.disconnect;
    });
    el = { scrollIntoView: vi.fn() };
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('대상까지 데려간다', () => {
    const { scrollToAnchor } = useAnchorScroll();

    scrollToAnchor(el);

    expect(el.scrollIntoView).toHaveBeenCalledTimes(1);
  });

  it('🔴 레이아웃이 변하면 **다시 맞춘다** — 위쪽이 자라면 한 번 간 위치는 틀려진다', () => {
    const { scrollToAnchor } = useAnchorScroll();

    scrollToAnchor(el);
    ro.callback(); // 리뷰 목록이 늦게 렌더돼 본문 높이가 변한 상황
    ro.callback();

    expect(el.scrollIntoView).toHaveBeenCalledTimes(3); // 최초 1 + 재조정 2
    expect(ro.observe).toHaveBeenCalled();
  });

  it('🔴 사용자가 움직이면 **즉시 손을 뗀다**(스크롤 하이재킹 방지)', () => {
    const { scrollToAnchor } = useAnchorScroll();

    scrollToAnchor(el);
    expect(ro.disconnect).not.toHaveBeenCalled(); // 아직은 따라가고 있어야 한다

    window.dispatchEvent(new Event('wheel'));

    // ⚠ 여기서 `ro.callback()` 을 직접 불러 «더 안 움직인다» 를 보이려 했다가 걷어냈다 —
    //    그건 **목(mock)을 시험하는 것**이다. 진짜 ResizeObserver 는 disconnect 뒤에 콜백을
    //    부르지 않으므로, 관찰할 수 있는 사실은 «관측을 끊었다» 하나다.
    expect(ro.disconnect).toHaveBeenCalled();
  });

  it('시한(2초)이 지나면 스스로 멈춘다 — 관측이 영원히 남지 않는다', () => {
    const { scrollToAnchor } = useAnchorScroll();

    scrollToAnchor(el);
    vi.advanceTimersByTime(2000);

    expect(ro.disconnect).toHaveBeenCalled();
  });

  it('화면을 벗어나면(cancel) 관측·타이머를 남기지 않는다', () => {
    const { scrollToAnchor, cancel } = useAnchorScroll();

    scrollToAnchor(el);
    cancel();

    expect(ro.disconnect).toHaveBeenCalled();
  });

  it('⚠ 대상이 없으면 **드러내고** 멈춘다 — `?.` 로 삼키면 기능이 조용히 사라진다(WA §2-8)', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const { scrollToAnchor } = useAnchorScroll();

    scrollToAnchor(null);

    // 2026-07-31 사고의 본질: `el?.scrollIntoView()` 가 no-op 이 되어 **콘솔에 아무것도 안 남았다.**
    expect(warn).toHaveBeenCalled();
    expect(global.ResizeObserver).not.toHaveBeenCalled();
  });

  it('연속으로 부르면 앞선 정렬을 정리하고 시작한다(관측이 겹쳐 쌓이지 않는다)', () => {
    const { scrollToAnchor } = useAnchorScroll();

    scrollToAnchor(el);
    scrollToAnchor(el);

    expect(ro.disconnect).toHaveBeenCalledTimes(1); // 두 번째 호출이 첫 번째를 걷어냈다
  });
});
