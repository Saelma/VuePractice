/**
 * jsdom 에서 DevExtreme 을 재우는 설정 (2026-08-14).
 *
 * 🔴 **증상**: 테스트는 전부 초록인데 전수 실행 끝에 처리되지 않은 에러가 뜬다 —
 *    `TypeError: window.getComputedStyle is not a function` (`devextreme/ui/themes.js`).
 *
 * 🔴 **원인**: DevExtreme 은 «테마 CSS 가 다 붙었나» 를 **10ms 간격 `setInterval` 로 최대 15초**
 *    확인한다(`waitForThemeLoad`). 확인 방법이 `.dx-theme-marker` 라는 빈 div 를 붙이고
 *    `getComputedStyle(...).fontFamily` 가 `dx.` 로 시작하는지 보는 것인데,
 *    **jsdom 엔 그 CSS 가 없으니 영원히 못 찾는다.** 그래서 폴링이 15초를 꽉 채우고,
 *    그 사이 테스트 파일이 끝나 jsdom 이 정리되면 `window` 가 사라진 자리에서 타이머가 깨어난다.
 *
 * ⚠ **컴포넌트 언마운트로는 안 멎는다** — 이 타이머는 위젯이 아니라 **모듈**이 돌리는 것이라
 *    마운트한 것을 다 정리해도 남는다(그래서 언마운트를 지켜도 에러가 났다).
 *
 * **처방**: 마커가 찾을 CSS 를 심어 준다. 한 번 읽히면 `handleLoaded()` 가 인터벌을 끊는다.
 * ⚠ 테마를 «흉내» 내는 게 아니다 — 값은 안 쓰이고 **「다 붙었다」는 신호로만** 쓰인다.
 */
const style = document.createElement('style');
style.textContent = ".dx-theme-marker { font-family: 'dx.generic.light'; }";
document.head.appendChild(style);
