import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 네 번째 화면 테스트 (2026-08-14) — 🔴 **DevExtreme 을 쓰는 첫 화면이다.**
//
// 앞의 셋은 전부 DevExtreme 을 안 쓰는 화면이었다(싼 것부터 했다). 남은 관리자 화면 대부분이
// 이 판단에 걸려 있어서, 추측하지 않고 **탐침을 하나 던져 실제로 확인했다**:
//
//   🔴 **DevExtreme 은 jsdom 에서 그대로 렌더된다** — `dx-datagrid` 가 서고 `dx-data-row` 가
//      실제로 그려지며, 셀 텍스트(`calculate-display-value` 결과 포함)가 `w.text()` 로 읽힌다.
//      → **스텁이 필요 없다.** 스텁으로 막았으면 정작 확인하려던 자리가 안 보였을 것이다
//        (아래 「대상 아이디」의 `—` 는 `DxColumn` 의 `calculate-display-value` 안에 있다).
//
//   ⚠ **딱 하나 걸린다: `attachTo` 를 쓰면 안 된다.** `mount(View, { attachTo: document.body })` 는
//      `TypeError: app.onUnmount is not a function` 으로 죽는다(@vue/test-utils 2.4.11 ↔ vue 3.3.0).
//      DevExtreme 과는 무관하다 — `attachTo` 를 빼면 그만이다.
//
// **이 화면을 고른 이유**: 2026-08-14 에 「대상 아이디」 빈칸을 `—` 로 바꾸는 변경을 넣었는데
// **테스트 없이 나갔다.** 그 변경은 상품 감사(대상이 회원이 아닌 첫 조작)와 한 세트라,
// 여기가 비는 것이 **정상**임을 화면이 말하지 않으면 다음 사람이 «데이터가 빠졌다» 로 읽는다.

const fetchAuditLogs = vi.fn();
vi.mock('../api/audit', async (importOriginal) => {
  // ⚠ 라벨·뱃지 맵은 **진짜를 쓴다**. 그게 `AuditAction.java` 와 대조되는 자리이고(audit.test.js),
  //    여기서 가짜로 갈아끼우면 이 화면이 그 맵을 쓴다는 사실 자체가 검증에서 빠진다.
  const real = await importOriginal();
  return { ...real, fetchAuditLogs: (...a) => fetchAuditLogs(...a) };
});

import AuditLogAdminView from './AuditLogAdminView.vue';
import { AUDIT_ACTION_LABEL } from '../api/audit';

function log(overrides = {}) {
  return {
    id: 'a1', action: 'MEMBER_SUSPEND', actorId: 'x', actorName: 'ZZ관리자',
    targetId: 'm1', targetLogin: 'zzuser', detail: null,
    createdAt: '2026-08-14T02:00:00Z',
    ...overrides,
  };
}

/**
 * ⚠ **마운트한 것은 반드시 언마운트한다.** DataGrid 는 뒤에서 계속 돌아서, 테스트 파일이 끝난 뒤
 * jsdom 이 정리되면 `TypeError: window.getComputedStyle is not a function` 이 **처리되지 않은
 * 에러**로 터진다(전수 실행에서만 보인다 — 단독 실행은 초록이다).
 */
let wrapper = null;

async function mountWith(rows) {
  fetchAuditLogs.mockResolvedValue({ content: rows, totalElements: rows.length });
  wrapper = mount(AuditLogAdminView); // ⚠ attachTo 를 쓰지 않는다(위 주석)
  // 🔴 **고정 대기(setTimeout(0))를 쓰지 않는다.** 처음엔 그렇게 썼는데 **단독 실행만 초록이고
  //    전수에서 깨졌다** — 다른 파일과 함께 돌면 그리드가 첫 로딩을 끝내는 데 더 걸린다.
  //    조건으로 기다린다(WA §3 의 「밟았는지 숫자로 판정한다」와 같은 결).
  await vi.waitUntil(() => wrapper.find('.dx-data-row').exists() || rows.length === 0,
    { timeout: 5000, interval: 20 });
  await flushPromises();
  return wrapper;
}

/**
 * 「대상 아이디」 칸에 실제로 입력한다.
 * ⚠ DevExtreme 은 기본적으로 `change` 에서 값을 확정하므로 `setValue` 만으로는 v-model 이 안 움직인다.
 * ⚠ 컴포넌트 이름(`findComponent({ name: 'DxTextBox' })`)으로는 못 찾는다 — 실측으로 확인했다.
 *    DOM 으로 잡는 편이 **사용자가 만지는 것과 같아** 더 정직하기도 하다.
 */
async function typeTargetLogin(w, value) {
  const input = w.find('input[placeholder="loginId 부분일치"]');
  await input.setValue(value);
  await input.trigger('change');
  await flushPromises();
}

describe('AuditLogAdminView', () => {
  beforeEach(() => fetchAuditLogs.mockReset());
  afterEach(() => { wrapper?.unmount(); wrapper = null; });

  it('🔴 그리드가 **실제로 그려진다** — 이 테스트의 전제(DevExtreme 실렌더)를 먼저 못 박는다', async () => {
    const w = await mountWith([log()]);

    expect(w.find('.dx-datagrid').exists()).toBe(true);
    expect(w.findAll('.dx-data-row')).toHaveLength(1);
    // ⚠ 이 단언이 깨지면 아래 것들이 «조용히 0건을 보고» 통과할 수 있다.
  });

  it('🔴 **대상이 회원이 아니면 「대상 아이디」에 `—` 를 그린다** — 빈칸이면 「데이터가 빠졌다」로 읽힌다', async () => {
    // 상품 삭제(2026-08-14, V50): targetId 는 상품이고 targetLogin 은 정상적으로 null 이다.
    const w = await mountWith([log({ action: 'PRODUCT_DELETE', targetLogin: null, detail: 'ZZ-지운상품' })]);

    const row = w.find('.dx-data-row').text();
    expect(row).toContain('—');
    expect(row).toContain('ZZ-지운상품'); // 「무엇을」은 detail 이 답한다
  });

  it('「내용」이 비어도 `—` 다 (같은 이유로 이미 그렇게 하고 있었다)', async () => {
    const w = await mountWith([log({ detail: null })]);

    expect(w.find('.dx-data-row').text()).toContain('—');
  });

  it('조작 종류를 **뱃지 문구로** 옮긴다 (날문자 enum 을 그대로 띄우지 않는다)', async () => {
    const w = await mountWith([log({ action: 'PRODUCT_DELETE' })]);

    const badge = w.find('.dx-data-row .badge');
    expect(badge.text()).toBe('상품 삭제');
    expect(badge.classes()).toContain('badge-warning'); // 유예 안에서 되돌릴 수 있다 = danger 아님
  });

  // 🔴 **여기서 경계를 하나 알았다: 그리드는 렌더되지만 «팝업 위젯» 은 안 열린다.**
  //
  //   「조작 종류 드롭다운의 목록이 라벨 맵에서 온다」를 **실제로 펼쳐서** 세려 했는데,
  //   `.dx-dropdowneditor-button` 에 `dxclick`·`mousedown`+`mouseup`+`click` 을 다 줘도
  //   `.dx-list-item` 이 **0개**다(팝업이 jsdom 에서 안 뜬다). **그래서 뺐다.**
  //
  //   ⚠ **프레임워크와 싸우는 테스트는 값보다 비용이 크다.** 게다가 이 자리가 노리던 드리프트
  //      (2026-08-10 에 라벨이 9개 중 3개만 있던 것)는 **`api/audit.test.js` 가 이미 지킨다** —
  //      그쪽이 `AuditAction.java` 를 읽어 키 집합을 대조한다.
  //   ⚠ 그래도 **남는 구멍이 있다**: «이 화면이 그 맵을 쓴다» 는 사실 자체는 아무도 안 지킨다
  //      (코드 한 줄이라 눈으로 보이지만, 누가 손으로 적은 목록으로 바꿔도 조용하다).
  //      → 필요해지면 그때는 **팝업이 아니라 위젯 인스턴스**로 가야 한다.

  // ── 검색 · 초기화 ──────────────────────────────────────────────

  it('검색은 **폼 값을 실어** 서버에 다시 묻는다', async () => {
    const w = await mountWith([log()]);
    fetchAuditLogs.mockClear();

    await typeTargetLogin(w, 'zzuser');
    await w.findAll('button').find((b) => b.text() === '검색').trigger('click');

    await vi.waitUntil(() => fetchAuditLogs.mock.calls.length > 0, { timeout: 5000, interval: 20 });
    expect(fetchAuditLogs.mock.calls.at(-1)[0]).toMatchObject({ targetLogin: 'zzuser' });
  });

  it('초기화는 폼을 비우고 **곧바로 다시 묻는다** (「지웠는데 목록은 그대로」가 안 되게)', async () => {
    const w = await mountWith([log()]);
    await typeTargetLogin(w, 'zzuser');
    fetchAuditLogs.mockClear();

    await w.findAll('button').find((b) => b.text() === '초기화').trigger('click');

    await vi.waitUntil(() => fetchAuditLogs.mock.calls.length > 0, { timeout: 5000, interval: 20 });
    const last = fetchAuditLogs.mock.calls.at(-1)[0];
    expect(last.targetLogin).toBe('');
    expect(last.action).toBeNull();
  });
});
