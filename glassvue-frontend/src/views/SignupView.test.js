import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 회원가입 화면 — 🔴 **사슬의 마지막 마디** (2026-09-01, BACKLOG J-3).
//
// 🔴 가입 후 이동이 **무조건 `router.push('/')`** 였다. 로그인 화면까지 복귀 경로를 들고 온 사람도
//    가입을 고르면 홈에 떨어졌다 — **신규 사용자가 기존 회원보다 나쁜 길**을 걸었다.
//
// ⚠ 여기서 보는 것은 «가입 뒤 어디로 가나» 뿐이다. 유효성·약관 문구는 이 항목이 건드리지 않았다.
// ✅ **자동 로그인은 원래 있었다**(가입 직후 `login` 을 부른다) — 착수 전 실측으로 확인했고,
//    그래서 보호 경로로 돌려보내도 라우터 가드가 다시 튕기지 않는다.

const push = vi.fn();
const signup = vi.fn();
const login = vi.fn();
const route = { path: '/signup', fullPath: '/signup', query: {} };

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  useRoute: () => route,
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
}));
vi.mock('../api/auth', () => ({
  signup: (...a) => signup(...a),
  login: (...a) => login(...a),
}));
vi.mock('../api/coupon', () => ({
  fetchWelcomeCoupon: () => Promise.resolve(null),
  couponDiscountText: () => '',
}));

import SignupView from './SignupView.vue';

const GLOBAL = {
  components: { RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' } },
};

let wrapper;

/** 아이디·비밀번호·닉네임·이메일(DevExtreme, change 로 확정) + 약관 동의(네이티브 체크박스). */
async function fillAndSubmit(w) {
  const inputs = w.findAll('input.dx-texteditor-input');
  const values = ['zzuser', 'Tulip-Harbor-72', 'ZZ닉네임', 'zz@example.com'];
  for (let i = 0; i < values.length; i += 1) {
    await inputs[i].setValue(values[i]);
    await inputs[i].trigger('change');
  }
  await w.findAll('input[type="checkbox"]')[1].setValue(true);   // [0] 전체동의 · [1] 이용약관(필수)
  await w.findAll('button').find((b) => b.text().includes('가입')).trigger('click');
  await flushPromises();
}

beforeEach(() => {
  push.mockReset();
  signup.mockReset().mockResolvedValue({});
  login.mockReset().mockResolvedValue({});
  route.query = {};
});
afterEach(() => wrapper?.unmount());

describe('SignupView — 가입 뒤 어디로 가나 (J-3)', () => {
  it('🔴 복귀 경로가 있으면 **왔던 자리로** 돌아간다', async () => {
    route.query = { redirect: '/products/p1' };
    wrapper = mount(SignupView, { global: GLOBAL });

    await fillAndSubmit(wrapper);

    expect(signup).toHaveBeenCalled();
    expect(login).toHaveBeenCalled();          // 자동 로그인이 먼저(보호 경로여도 안 튕긴다)
    expect(push).toHaveBeenLastCalledWith('/products/p1');
  });

  it('복귀 경로가 없으면 홈으로 — 예전 동작 그대로다', async () => {
    wrapper = mount(SignupView, { global: GLOBAL });
    await fillAndSubmit(wrapper);
    expect(push).toHaveBeenLastCalledWith('/');
  });
});
