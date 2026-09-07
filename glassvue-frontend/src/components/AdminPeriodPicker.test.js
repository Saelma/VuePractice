import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 관리자 **기간 선택** 공용 컨트롤 (B-26 잔여, 2026-09-07).
//
// 🔴 **여기서 지키는 것은 「기준점이 서버다」이다.** 프리셋(「이번 달」)이 브라우저 시계를 쓰면
//    같은 버튼이 사람마다 다른 기간을 뜻하게 되고, **그 어긋남은 화면에 안 보인다**.
//    그래서 ①서버 날짜로 계산하는지 ②못 받으면 잠그는지(브라우저 시계로 넘어가지 않는지)를 본다.
//
// ⚠ 두 번째가 특히 중요하다 — 실패 시 `new Date()` 로 넘어가는 «친절한» 폴백이 가장 위험하다.

const fetchServerToday = vi.fn();
vi.mock('../api/period', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, fetchServerToday: (...a) => fetchServerToday(...a) };
});

import AdminPeriodPicker from './AdminPeriodPicker.vue';

async function open(props = {}) {
  const wrapper = mount(AdminPeriodPicker, { props });
  await flushPromises();
  return wrapper;
}

const presetButton = (w, label) =>
  w.findAll('button').find((b) => b.text() === label);

describe('AdminPeriodPicker', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchServerToday.mockResolvedValue({ today: '2026-09-07' });
  });

  it('프리셋은 서버가 준 «오늘»로 계산한다 — 브라우저 시계가 아니다', async () => {
    const w = await open();
    await presetButton(w, '오늘').trigger('click');

    expect(w.emitted('change')[0][0]).toEqual({ from: '2026-09-07', to: '2026-09-07' });
  });

  it('«이번 달»은 1일부터 서버의 오늘까지다', async () => {
    const w = await open();
    await presetButton(w, '이번 달').trigger('click');

    expect(w.emitted('change')[0][0]).toEqual({ from: '2026-09-01', to: '2026-09-07' });
  });

  it('«지난 달»은 말일을 직접 세지 않는다 — 8월은 31일까지다', async () => {
    const w = await open();
    await presetButton(w, '지난 달').trigger('click');

    expect(w.emitted('change')[0][0]).toEqual({ from: '2026-08-01', to: '2026-08-31' });
  });

  it('🔴 서버 날짜를 못 받으면 프리셋을 잠근다 — 브라우저 시계로 넘어가지 않는다', async () => {
    fetchServerToday.mockRejectedValue(new Error('네트워크'));
    const w = await open();

    expect(presetButton(w, '오늘').attributes('disabled')).toBeDefined();
    await presetButton(w, '오늘').trigger('click');
    expect(w.emitted('change')).toBeUndefined();

    // ⚠ 대조군 — 직접 고르기는 계속 된다(전부 잠그는 것이 아니다).
    const [from, to] = w.findAll('input[type="date"]');
    await from.setValue('2026-01-01');
    await to.setValue('2026-01-31');
    expect(w.emitted('change')[0][0]).toEqual({ from: '2026-01-01', to: '2026-01-31' });
  });

  it('한쪽만 고른 상태로는 안 보낸다 — 반쪽 기간은 질문이 안 된다', async () => {
    const w = await open();
    const [from] = w.findAll('input[type="date"]');

    await from.setValue('2026-09-01');
    expect(w.emitted('change')).toBeUndefined();
  });

  it('지금 고른 구간과 같은 프리셋이 활성으로 보인다', async () => {
    fetchServerToday.mockResolvedValue({ today: '2026-09-20' });
    const w = await open({ from: '2026-09-01', to: '2026-09-20' });

    expect(presetButton(w, '이번 달').classes()).toContain('border-ink-900');
    expect(presetButton(w, '오늘').classes()).not.toContain('border-ink-900');
    expect(presetButton(w, '7일').classes()).not.toContain('border-ink-900');
  });

  // 🔴 **달마다 겹치는 날이 있다.** 9월 7일에는 「7일」(09-01~09-07)과 「이번 달」(09-01~09-07)이
  //    **완전히 같은 구간**이다. `matchedPreset` 은 **먼저 오는 것**을 돌려주므로 「7일」이 켜진다.
  //    ⚠ 결함이 아니라 **구간이 같아서 구별할 수 없는 것**이다 — 그런 날 「이번 달」이 안 켜진다고
  //    이상하게 볼 사람을 위해 여기에 고정해 둔다(고치려면 «무엇을 눌렀는지» 를 따로 기억해야 하고,
  //    그건 프리셋을 상태로 만드는 일이라 지금 값에 비해 비싸다).
  it('구간이 같은 프리셋이 둘이면 목록에서 먼저 오는 쪽이 켜진다', async () => {
    const w = await open({ from: '2026-09-01', to: '2026-09-07' });   // 오늘 = 09-07

    expect(presetButton(w, '7일').classes()).toContain('border-ink-900');
    expect(presetButton(w, '이번 달').classes()).not.toContain('border-ink-900');
  });

  it('«기간 해제»는 고른 것이 있을 때만 보이고, 빈 값으로 알린다', async () => {
    const empty = await open();
    expect(presetButton(empty, '기간 해제')).toBeUndefined();

    const w = await open({ from: '2026-09-01', to: '2026-09-07' });
    await presetButton(w, '기간 해제').trigger('click');
    expect(w.emitted('change')[0][0]).toEqual({ from: '', to: '' });
  });
});
