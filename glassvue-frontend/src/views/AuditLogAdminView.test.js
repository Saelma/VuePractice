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

import { DxSelectBox } from 'devextreme-vue/select-box';
import AuditLogAdminView from './AuditLogAdminView.vue';
import { AUDIT_ACTION_LABEL, AUDIT_TARGET_TYPE_LABEL } from '../api/audit';

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
  // ⚠ **대기 시간은 `testTimeout` 보다 짧아야 한다.** 처음엔 둘 다 5초로 같았는데,
  //    DevExtreme 화면이 둘이 된 순간(2026-08-14 `MemberDetailAdminView`) 전수에서 넘어갔고
  //    «타임아웃» 만 뜨고 **무엇을 기다리다 죽었는지는 안 나왔다**. 바깥을 20초로 벌리고 여기를
  //    12초로 뒀다 — 이제 조건 대기가 먼저 끝나 이유를 말한다(`vitest.config.mjs` 주석).
  await vi.waitUntil(() => wrapper.find('.dx-data-row').exists() || rows.length === 0,
    { timeout: 12_000, interval: 20 });
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

/**
 * 「대상 종류」 SelectBox 를 **위젯 인스턴스로** 움직인다 (2026-08-21).
 *
 * 🔴 **이 파일이 «필요해지면 그때 가겠다» 고 적어 둔 그 길이다**(아래 팝업 주석). 08-20 에
 * 필터가 생겼고 **08-21 에 값이 둘 늘었는데**(CATEGORY·NOTICE) 그걸 보는 테스트가 없었다.
 *
 * ⚠ 팝업(`.dx-list-item`)은 jsdom 에서 안 열린다 — 그건 이미 밟아서 확인된 경계다.
 * 대신 **DevExtreme 인스턴스의 `option('value', …)`** 로 값을 넣는다. 이건 사용자가 항목을 고를 때
 * 위젯이 스스로 하는 일과 **같은 경로**라(`update:value` 가 나가고 v-model 이 움직인다)
 * `$emit` 을 흉내내는 것보다 정직하다.
 */
function selectBoxes(w) {
  return w.findAllComponents(DxSelectBox);
}
async function pickTargetType(w, value) {
  // [0] 조작 종류 · [1] 대상 종류 — 템플릿 순서다.
  selectBoxes(w)[1].vm.instance.option('value', value);
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

  // ── 대상 종류 (2026-08-20 V53 · 2026-08-21 V56 로 값이 둘 늘었다) ──────────────

  it('🔴 「대상」 열을 **라벨로** 옮긴다 — 오늘 늘어난 값도 날문자로 안 뜬다', async () => {
    // V56 이 CATEGORY 를 세웠다. 라벨이 없으면 화면에 `CATEGORY` 가 그대로 뜬다 —
    // 2026-08-10 에 조작 종류에서 났던 그 사고의 「대상 종류」 판이다.
    const w = await mountWith([log({
      action: 'CATEGORY_DELETE', targetType: 'CATEGORY', targetLogin: null, detail: 'ZZ-없앨분류',
    })]);

    const row = w.find('.dx-data-row').text();
    expect(row).toContain('카테고리');
    expect(row).not.toContain('CATEGORY');
    // ⚠ 대상이 회원이 아니라 「대상 아이디」는 정상적으로 비고 `—` 가 된다.
    //    🔴 그 빈칸의 **이유를 「대상」 열이 설명한다** — 두 열이 한 쌍인 이유다.
    expect(row).toContain('—');
  });

  it('공지도 라벨로 뜬다 (V56 의 나머지 한 값)', async () => {
    const w = await mountWith([log({
      action: 'NOTICE_UPDATE', targetType: 'NOTICE', targetLogin: null, detail: '변경 없음',
    })]);

    expect(w.find('.dx-data-row').text()).toContain('공지');
  });

  it('🔴 대상 종류 **선택지가 라벨 맵에서 온다** — 손으로 적은 목록으로 바뀌면 여기서 걸린다', async () => {
    // ⚠ 이 화면이 그 맵을 «쓴다» 는 사실은 그전까지 **아무도 안 지켰다**(아래 팝업 주석이 남긴 구멍).
    //    api/audit.test.js 는 맵과 enum 을 대조할 뿐, 화면이 그 맵을 쓰는지는 모른다.
    const w = await mountWith([log()]);

    const items = selectBoxes(w)[1].props('items');
    expect(items[0]).toEqual({ value: null, label: '전체' }); // 「전체」가 맨 앞이어야 비울 수 있다
    expect(items.slice(1)).toEqual(
      Object.entries(AUDIT_TARGET_TYPE_LABEL).map(([value, label]) => ({ value, label })),
    );
    // 🔴 오늘 늘린 둘이 실제로 고를 수 있는지 못 박는다 — 남겨도 못 찾으면 소용이 없다.
    expect(items.map((i) => i.value)).toEqual(expect.arrayContaining(['CATEGORY', 'NOTICE']));
  });

  it('조작 종류 선택지도 라벨 맵에서 온다 (같은 이유)', async () => {
    const w = await mountWith([log()]);

    const items = selectBoxes(w)[0].props('items');
    expect(items.slice(1)).toEqual(
      Object.entries(AUDIT_ACTION_LABEL).map(([value, label]) => ({ value, label })),
    );
    expect(items.map((i) => i.value)).toEqual(
      expect.arrayContaining(['CATEGORY_CREATE', 'NOTICE_DELETE', 'INQUIRY_ANSWER']),
    );
  });

  // ── 검색 · 초기화 ──────────────────────────────────────────────

  it('검색은 **폼 값을 실어** 서버에 다시 묻는다', async () => {
    const w = await mountWith([log()]);
    fetchAuditLogs.mockClear();

    await typeTargetLogin(w, 'zzuser');
    await w.findAll('button').find((b) => b.text() === '검색').trigger('click');

    await vi.waitUntil(() => fetchAuditLogs.mock.calls.length > 0, { timeout: 12_000, interval: 20 });
    expect(fetchAuditLogs.mock.calls.at(-1)[0]).toMatchObject({ targetLogin: 'zzuser' });
  });

  it('🔴 **대상 종류도 검색에 실린다** — 회원 아닌 행을 좁히는 유일한 수단이다', async () => {
    // ⚠ 그전까지 이 화면 테스트는 targetLogin 만 봤다. targetType 이 payload 에서 빠져도
    //    **화면은 멀쩡하고 목록도 그려진다** — 그냥 «안 좁혀질» 뿐이라 조용하다.
    const w = await mountWith([log()]);
    fetchAuditLogs.mockClear();

    await pickTargetType(w, 'CATEGORY');
    await w.findAll('button').find((b) => b.text() === '검색').trigger('click');

    await vi.waitUntil(() => fetchAuditLogs.mock.calls.length > 0, { timeout: 12_000, interval: 20 });
    expect(fetchAuditLogs.mock.calls.at(-1)[0]).toMatchObject({ targetType: 'CATEGORY' });
  });

  it('초기화는 폼을 비우고 **곧바로 다시 묻는다** (「지웠는데 목록은 그대로」가 안 되게)', async () => {
    const w = await mountWith([log()]);
    await typeTargetLogin(w, 'zzuser');
    fetchAuditLogs.mockClear();

    await w.findAll('button').find((b) => b.text() === '초기화').trigger('click');

    await vi.waitUntil(() => fetchAuditLogs.mock.calls.length > 0, { timeout: 12_000, interval: 20 });
    const last = fetchAuditLogs.mock.calls.at(-1)[0];
    expect(last.targetLogin).toBe('');
    expect(last.action).toBeNull();
  });

  it('🔴 초기화는 **대상 종류도** 비운다 — 안 지우면 「전체로 돌렸는데 안 늘어난다」가 된다', async () => {
    // ⚠ reset() 이 세 필드를 손으로 열거한다. 필드가 늘 때 하나를 빠뜨리기 딱 좋은 모양이고,
    //    빠뜨려도 **아무것도 안 터진다** — 목록이 조용히 좁혀진 채로 남는다.
    const w = await mountWith([log()]);
    await pickTargetType(w, 'NOTICE');
    fetchAuditLogs.mockClear();

    await w.findAll('button').find((b) => b.text() === '초기화').trigger('click');

    await vi.waitUntil(() => fetchAuditLogs.mock.calls.length > 0, { timeout: 12_000, interval: 20 });
    expect(fetchAuditLogs.mock.calls.at(-1)[0].targetType).toBeNull();
  });
});
