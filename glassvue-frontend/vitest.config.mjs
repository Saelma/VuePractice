import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';

// 프론트 단위 테스트. api 헬퍼·스토어·client 같은 순수 로직 + .vue 컴포넌트·화면을
// @vue/test-utils로 마운트해 검증한다.
//
// 🔴 **DevExtreme 도 마운트한다** (2026-08-14 에 실측으로 닫았다 — 이 줄은 그전까지
//    *"jsdom에서 불안정해 여전히 제외한다"* 였다). `dx-datagrid` 가 서고 셀 텍스트가 읽힌다.
//    ⚠ 대신 함정 셋이 있다 — **`attachTo` 금지 · 고정 대기 말고 조건 대기 · 반드시 언마운트**.
//    첫 사례와 근거는 `src/views/AuditLogAdminView.test.js` 머리 주석과 TROUBLESHOOTING 에 있다.
//    ⚠ 남은 경계 하나: **팝업 위젯(드롭다운 목록)은 jsdom 에서 안 열린다.**
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom', // localStorage·window.location + 컴포넌트 마운트 DOM
    include: ['src/**/*.test.js'],
    // 🔴 **DevExtreme 의 테마 폴링을 재운다.** 안 하면 두 가지가 한꺼번에 온다 —
    //    ①전수 끝에 «전부 초록인데 처리되지 않은 에러» ②DevExtreme 화면이 **느려진다**.
    //    ⚠ 둘의 원인이 같다(그 파일 주석). 2026-08-14 실측: 감사 이력 화면 6건이
    //    전수에서 **15.3초 → 3.8초**(테스트 실행 시간). 한 건이 5초를 넘겨 **넘어지기까지 했다.**
    setupFiles: ['./src/test/setup-devextreme.js'],
    // ⚠ 위 setupFiles 를 넣기 **전에** 늘린 값이다. 그때는 테마 폴링 때문에 전수에서 5초를 넘겼고,
    //    ⚠ 하필 테스트 안의 `vi.waitUntil(..., { timeout: 5000 })` 이 **testTimeout 과 같아서**
    //       조건 대기가 자기 메시지를 낼 기회조차 없었다 — «타임아웃» 만 뜨고 **무엇을 기다렸는지는
    //       안 나왔다.** 🔴 **원인은 폴링이었고 이 값은 증상 쪽이었다** — 지금은 다 1.5초 안에 끝난다.
    //    그래도 남겨 둔다: 안쪽 대기(12초)보다 바깥이 넓어야 **실패 이유가 보인다.**
    testTimeout: 20_000,
  },
});
