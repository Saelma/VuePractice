/**
 * 앵커로 들어온 요청을 그 자리까지 데려간다 (WORKING-AGREEMENTS §2-9).
 *
 * ⚠ **한 번 계산해서 끝내면 엉뚱한 곳에 선다.** 위쪽 섹션이 자기 데이터를 나중에 받아 렌더되면서
 * 목표를 아래로 밀어내는데, 스크롤은 **시작할 때 계산한 위치**에서 멈추기 때문이다.
 * → 레이아웃이 잠잠해질 때까지 다시 맞추고(ResizeObserver), 짧은 시한과 **사용자 입력**에서
 *   즉시 손을 뗀다(스크롤 하이재킹 방지). 화면을 벗어날 때 정리하는 것까지가 한 세트다.
 *
 * ⚠ **`el?.` 로 삼키지 않는다**(WA §2-8). 여기서 요소가 없다는 건 «앵커가 안 먹었다» 는 뜻이라
 * 조용히 넘어가면 에러도 로그도 없이 기능이 사라진다 — 2026-07-31 에 실제로 그렇게 잃었다.
 *
 * 2026-08-07(G-3 3단계)에 **두 번째 사용처**가 생겨 공용으로 뽑았다. 규칙이 미묘해서 복사본이
 * 생기면 한쪽만 고쳐진다 — 상품 상세(`#inquiries`)와 고객센터(`#inquiry-{id}`) 둘 다 이걸 쓴다.
 */

// 앵커 정렬을 포기하는 신호 — 사용자가 직접 움직였으면 그 순간 손을 뗀다.
const CANCEL_EVENTS = ['wheel', 'touchstart', 'keydown'];
const SETTLE_MS = 2000;

export function useAnchorScroll() {
  let stop = null;

  /** @param {HTMLElement|null} el 데려갈 요소. 없으면 **드러내고** 멈춘다(삼키지 않는다). */
  function scrollToAnchor(el) {
    if (!el) {
      console.warn('[anchor] 대상 요소가 없어 앵커 이동을 건너뜁니다 — 렌더 전에 불렀을 수 있습니다.');
      return;
    }
    cancel(); // 앞선 정렬이 살아 있으면 정리하고 시작한다(연속 호출 대비)

    const go = () => el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    go();

    const ro = new ResizeObserver(go); // 본문 높이가 변할 때마다 다시 맞춘다
    ro.observe(document.body);

    const onCancel = () => cancel();
    const timer = setTimeout(() => cancel(), SETTLE_MS);

    stop = () => {
      ro.disconnect();
      clearTimeout(timer);
      CANCEL_EVENTS.forEach((t) => window.removeEventListener(t, onCancel));
      stop = null;
    };
    CANCEL_EVENTS.forEach((t) => window.addEventListener(t, onCancel, { once: true, passive: true }));
  }

  /** 화면을 벗어나거나 다시 정렬할 때 관측·타이머를 남기지 않는다. */
  function cancel() {
    stop?.();
  }

  return { scrollToAnchor, cancel };
}
