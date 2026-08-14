import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 다섯 번째 화면 테스트 (2026-08-14). 패턴은 `views/AuditLogAdminView.test.js` 에서 가져왔다
// (DevExtreme 실렌더 · `attachTo` 금지 · 언마운트 — 그 셋은 거기 주석과 TROUBLESHOOTING 에 있다).
//
// 🔴 **이 화면을 고른 이유: 되돌릴 수 없는 조작이 여기 모여 있다.** 정지 · 역할 변경 · **강제 삭제**.
//    ⚠ 이월은 「MemberAdminView」라고 적었지만 **거기엔 조작이 없다**(조회 전용 — 그 파일 머리 주석이
//    *"정지·역할변경은 다음 단계"* 라고 적어 뒀다). 조작은 **상세**에 있다. 여기가 맞는 자리다.
//
// 🔴 **그리고 이 화면의 방어 절반은 화면에만 있다.** 서버가 최종 방어선인 건 맞지만(400 으로 막는다),
//    «본인은 자기를 정지 못 한다» · «최상위 관리자는 아무도 못 건드린다» · «삭제는 최상위만» 은
//    **버튼을 감추는 computed 넷**(`isSelf`·`targetIsSuper`·`canSuspend`·`canChangeRole`)이 그리고,
//    **그 넷을 아무 테스트도 안 지키고 있었다.** 여기서 틀리면 관리자가 «눌렀는데 400» 을 만난다 —
//    막히긴 하지만 **화면이 거짓말을 한 것**이다.
//
// ⚠ 계층 규칙의 출처는 2026-07-28 「엄격 분리」다: 일반 ADMIN 은 USER 만 정지 · 역할 변경은
//    SUPER_ADMIN 전용 · SUPER_ADMIN 계정은 아무도 못 건드림.

const push = vi.fn();
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: 'm1' } }),
  useRouter: () => ({ push: (...a) => push(...a) }),
}));

const fetchAdminMember = vi.fn();
const suspendMember = vi.fn();
const unsuspendMember = vi.fn();
const changeMemberRole = vi.fn();
const deleteMember = vi.fn();
vi.mock('../api/member', async (importOriginal) => {
  // ⚠ `roleText`(ROLE_LABEL)는 **진짜를 쓴다** — 화면이 날문자 enum 을 안 띄운다는 것까지 보려면
  //    맵을 가짜로 갈아끼우면 안 된다(AuditLogAdminView 테스트와 같은 이유).
  const real = await importOriginal();
  return {
    ...real,
    fetchAdminMember: (...a) => fetchAdminMember(...a),
    suspendMember: (...a) => suspendMember(...a),
    unsuspendMember: (...a) => unsuspendMember(...a),
    changeMemberRole: (...a) => changeMemberRole(...a),
    deleteMember: (...a) => deleteMember(...a),
  };
});

const fetchAdminMemberPointAccount = vi.fn();
vi.mock('../api/point', async (importOriginal) => {
  const real = await importOriginal();
  return {
    ...real,
    fetchAdminMemberPointAccount: (...a) => fetchAdminMemberPointAccount(...a),
    fetchAdminMemberPointHistory: vi.fn(async () => ({ content: [], totalElements: 0 })),
  };
});
vi.mock('../api/order', async (importOriginal) => {
  const real = await importOriginal();
  return { ...real, fetchAdminMemberOrders: vi.fn(async () => ({ content: [], totalElements: 0 })) };
});

import MemberDetailAdminView from './MemberDetailAdminView.vue';
import { authState } from '../stores/auth';

/** 서버가 주는 회원 하나(`AdminMemberResponse`). */
function member(overrides = {}) {
  return {
    id: 'm1', loginId: 'zzuser', nickname: 'ZZ회원', email: 'zz@example.com',
    role: 'USER', suspended: false, createdAt: '2026-08-01T00:00:00Z',
    termsAgreedAt: '2026-08-01T00:00:00Z', marketingAgreedAt: null,
    ...overrides,
  };
}

const POINT = {
  grade: 'BRONZE', earnPercent: 1, balance: 1000, totalPurchase: 50_000,
  nextGrade: 'SILVER', amountToNextGrade: 50_000,
};

/** ⚠ 마운트한 것은 반드시 언마운트한다 — DataGrid 가 뒤에서 계속 돈다(AuditLogAdminView 주석). */
let wrapper = null;

/**
 * `viewer` 로 로그인한 채 `target` 회원의 상세를 연다.
 * ⚠ `authState` 는 **가짜로 안 바꾼다** — 진짜 스토어를 그대로 쓴다. 이 화면이 보는 것이 그것이고,
 *   `isSelf` 는 «id 가 같은가» 라 스토어 모양이 틀리면 조용히 항상 false 가 된다.
 */
async function open(target, viewer = { id: 'super1', role: 'SUPER_ADMIN' }) {
  authState.user = viewer;
  fetchAdminMember.mockResolvedValue(target);
  wrapper = mount(MemberDetailAdminView); // ⚠ attachTo 를 쓰지 않는다
  await flushPromises();
  return wrapper;
}

/** 「관리」 줄의 버튼 문구만 (← 목록 · 그리드의 「상세」는 뺀다). */
function actionLabels(w) {
  return w.findAll('.card button').map((b) => b.text());
}
function clickAction(w, label) {
  return w.findAll('.card button').find((b) => b.text() === label).trigger('click');
}

describe('MemberDetailAdminView — 되돌릴 수 없는 조작이 있는 화면', () => {
  beforeEach(() => {
    vi.stubGlobal('confirm', vi.fn(() => true));
    push.mockReset();
    [fetchAdminMember, suspendMember, unsuspendMember, changeMemberRole, deleteMember]
      .forEach((m) => m.mockReset());
    fetchAdminMemberPointAccount.mockReset().mockResolvedValue(POINT);
  });
  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    vi.unstubAllGlobals();
    authState.user = null;
  });

  // ── 누가 무엇을 할 수 있는가 (버튼이 있고 없고) ────────────────

  it('최상위 관리자가 일반 회원을 보면 **정지 · 역할 변경 · 삭제가 모두 있다**', async () => {
    const w = await open(member());

    expect(actionLabels(w)).toEqual(['정지', '관리자로 승격', '회원 삭제']);
  });

  it('🔴 **본인 계정에는 아무 버튼도 없다** — 자기를 정지하면 스스로 잠긴다', async () => {
    const w = await open(member({ id: 'super1', role: 'SUPER_ADMIN' }), { id: 'super1', role: 'SUPER_ADMIN' });

    expect(actionLabels(w)).toEqual([]);
    expect(w.text()).toContain('본인 계정입니다');
    // 🔴 여기가 미묘하다: 「회원 삭제」에는 `v-if="isSelf"` 가 **없다**. 바깥 `v-else` 블록이 통째로
    //    안 그려져서 사라지는 것뿐이다 — 누가 그 template 을 풀면 **자기 계정 삭제 버튼이 살아난다.**
    expect(actionLabels(w)).not.toContain('회원 삭제');
  });

  it('🔴 **최상위 관리자 계정은 최상위 관리자도 못 건드린다** — 서로 잠그는 일이 없게', async () => {
    const w = await open(member({ id: 'other', role: 'SUPER_ADMIN' }));

    expect(actionLabels(w)).toEqual([]);
    expect(w.text()).toContain('최상위 관리자 계정은 정지·역할 변경할 수 없습니다');
  });

  it('일반 관리자는 **일반 회원을 정지까지만** 한다 (역할 변경 · 삭제는 없다)', async () => {
    const w = await open(member(), { id: 'admin1', role: 'ADMIN' });

    expect(actionLabels(w)).toEqual(['정지']);
  });

  it('🔴 일반 관리자는 **다른 관리자를 못 건드린다** — 버튼을 감추고 그 이유를 적는다', async () => {
    const w = await open(member({ id: 'other', role: 'ADMIN' }), { id: 'admin1', role: 'ADMIN' });

    expect(actionLabels(w)).toEqual([]);
    expect(w.text()).toContain('최상위 관리자만 정지·역할 변경할 수 있습니다');
  });

  // ── 강제 삭제 (B-24) — 되돌릴 수 없다 ─────────────────────────

  it('🔴 삭제는 **확인을 두 번** 받는다 — 두 번째에서 멈추면 지우지 않는다', async () => {
    const confirmFn = vi.fn().mockReturnValueOnce(true).mockReturnValueOnce(false);
    vi.stubGlobal('confirm', confirmFn);
    const w = await open(member());

    await clickAction(w, '회원 삭제');
    await flushPromises();

    expect(confirmFn).toHaveBeenCalledTimes(2);
    expect(deleteMember).not.toHaveBeenCalled(); // 🔴 한 번만 물었으면 여기서 지워진다
    expect(push).not.toHaveBeenCalled();
  });

  it('🔴 첫 확인은 **지워지는 것과 남는 것을 둘 다** 말한다 — 눌러 보고 알게 하지 않는다', async () => {
    const confirmFn = vi.fn(() => false);
    vi.stubGlobal('confirm', confirmFn);
    const w = await open(member());

    await clickAction(w, '회원 삭제');

    const text = confirmFn.mock.calls[0][0];
    expect(text).toContain('ZZ회원(zzuser)');          // 누구를 지우는지
    expect(text).toContain('함께 지워집니다');          // 배송지·적립금·찜…
    expect(text).toContain('남습니다: 주문 내역');      // ⚠ 「주문도 날아가나?」의 답이 여기 있어야 한다
    expect(text).toContain('되돌릴 수 없습니다');
  });

  it('지우고 나면 **목록으로 나간다** — 사라진 회원의 상세에 머물 이유가 없다', async () => {
    deleteMember.mockResolvedValue(undefined);
    const w = await open(member());

    await clickAction(w, '회원 삭제');
    await flushPromises();

    expect(deleteMember).toHaveBeenCalledWith('m1');
    expect(push).toHaveBeenCalledWith('/admin/members');
  });

  it('🔴 삭제가 실패하면 **머물러서 이유를 말하고, 다시 누를 수 있게** 돌아온다', async () => {
    deleteMember.mockRejectedValue(new Error('최상위 관리자는 삭제할 수 없습니다'));
    const w = await open(member());

    await clickAction(w, '회원 삭제');
    await flushPromises();

    expect(push).not.toHaveBeenCalled(); // 안 지워졌는데 목록으로 보내면 지워진 줄 안다
    expect(w.find('.alert-error').text()).toContain('최상위 관리자는 삭제할 수 없습니다');
    // ⚠ busy 를 안 풀면 화면이 잠긴 채로 남는다(새로고침해야 풀린다).
    expect(w.findAll('.card button').find((b) => b.text() === '회원 삭제').attributes('disabled'))
      .toBeUndefined();
  });

  // ── 정지 / 해제 ───────────────────────────────────────────────

  it('🔴 **정지에는 경고가 붙고 해제에는 안 붙는다** — 한쪽만 되돌리기 어렵다', async () => {
    const confirmFn = vi.fn(() => false);
    vi.stubGlobal('confirm', confirmFn);

    const w1 = await open(member());
    await clickAction(w1, '정지');
    expect(confirmFn.mock.calls[0][0]).toContain('로그인·주문이 막히고');
    w1.unmount();

    confirmFn.mockClear();
    const w2 = await open(member({ suspended: true }));
    await clickAction(w2, '정지 해제');
    expect(confirmFn.mock.calls[0][0]).toContain('정지 해제');
    expect(confirmFn.mock.calls[0][0]).not.toContain('막히고'); // 푸는 데 겁을 줄 이유가 없다
  });

  it('확인을 취소하면 **서버를 부르지 않는다**', async () => {
    vi.stubGlobal('confirm', vi.fn(() => false));
    const w = await open(member());

    await clickAction(w, '정지');
    await flushPromises();

    expect(suspendMember).not.toHaveBeenCalled();
  });

  it('🔴 정지하면 **응답으로 화면을 갈아끼운다** — 다시 읽지 않고도 상태가 맞는다', async () => {
    suspendMember.mockResolvedValue(member({ suspended: true }));
    const w = await open(member());
    expect(w.text()).toContain('활성');

    await clickAction(w, '정지');
    await flushPromises();

    expect(suspendMember).toHaveBeenCalledWith('m1');
    expect(w.text()).toContain('정지됨');
    // 버튼도 따라 뒤집힌다 — 안 그러면 「정지」를 또 누르게 된다.
    expect(actionLabels(w)).toContain('정지 해제');
    expect(fetchAdminMember).toHaveBeenCalledTimes(1); // 목록을 다시 안 읽는다
  });

  it('역할을 바꾸면 **뱃지 문구도 따라간다** (날문자 `ADMIN` 을 그대로 띄우지 않는다)', async () => {
    changeMemberRole.mockResolvedValue(member({ role: 'ADMIN' }));
    const w = await open(member());

    await clickAction(w, '관리자로 승격');
    await flushPromises();

    expect(changeMemberRole).toHaveBeenCalledWith('m1', 'ADMIN');
    expect(w.text()).toContain('관리자');
    expect(w.text()).not.toContain('ADMIN');
    expect(actionLabels(w)).toContain('일반으로 강등');
  });

  // ── 같은 null, 다른 뜻 (B-21) ────────────────────────────────

  it('🔴 약관 `null` 은 **「기록 없음」** 이다 — 거부한 게 아니라 **물어본 적이 없다**', async () => {
    // V37 이전 가입자에겐 동의 절차 자체가 없었다.
    const w = await open(member({ termsAgreedAt: null, marketingAgreedAt: null }));

    expect(w.text()).toContain('기록 없음 (동의 절차 이전 가입)');
    // 🔴 그리고 **같은 null 인 마케팅은 「미동의」다** — 그쪽은 선택이라 안 한 것이 맞다.
    //    둘을 같은 문구로 그리면 화면이 거짓말을 한다.
    expect(w.text()).toContain('미동의');
  });

  it('동의 기록이 있으면 **그 시각을 그린다**', async () => {
    const w = await open(member({ marketingAgreedAt: '2026-08-01T00:00:00Z' }));

    expect(w.text()).not.toContain('기록 없음');
    expect(w.text()).toContain('동의');
  });

  // ── 조각 하나가 없어도 나머지는 보인다 ─────────────────────────

  it('🔴 적립금을 못 읽어도 **회원 정보와 조작은 그대로 있다** — 조각 하나에 화면 전체를 걸지 않는다', async () => {
    fetchAdminMemberPointAccount.mockRejectedValue(new Error('point down'));
    const w = await open(member());

    expect(w.text()).toContain('적립금 정보를 불러오지 못했습니다');
    expect(w.text()).toContain('ZZ회원');
    expect(actionLabels(w)).toContain('회원 삭제'); // 조작도 살아 있다
    expect(w.find('.alert-error').exists()).toBe(false); // 이건 화면 전체의 실패가 아니다
  });

  it('회원을 못 읽으면 **말한다** — 빈 화면은 「그런 회원 없음」으로 읽힌다', async () => {
    fetchAdminMember.mockRejectedValue(new Error('회원을 찾을 수 없습니다'));
    authState.user = { id: 'super1', role: 'SUPER_ADMIN' };
    wrapper = mount(MemberDetailAdminView);
    await flushPromises();

    expect(wrapper.find('.alert-error').text()).toContain('회원을 찾을 수 없습니다');
    expect(wrapper.findAll('.card')).toHaveLength(0);
  });
});
