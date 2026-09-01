import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 로그인 화면 — 🔴 **복귀 경로가 여기서 끊기던 자리** (2026-09-01, BACKLOG J-3).
//
// 🔴 로그인 자체는 `redirect` 를 지켰는데(`route.query.redirect || '/'`), **「회원가입」으로 넘어가는
//    순간 그 값이 버려졌다**(`to="/signup"`). 그래서 찜을 누르다 로그인까지 잘 온 사람이
//    **가입을 고르는 순간 경로를 잃었다** — 신규 사용자가 기존 회원보다 나쁜 길을 걸었다.
//
// ⚠ 여기서 보는 것은 «값이 이어지는가» 뿐이다. 규칙 자체는 composables/useLoginRedirect.test.js.

const push = vi.fn();
const login = vi.fn();
const route = { path: '/login', fullPath: '/login', query: {} };

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  useRoute: () => route,
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
}));
vi.mock('../api/auth', () => ({ login: (...a) => login(...a) }));

import LoginView from './LoginView.vue';

let wrapper;

/**
 * ⚠ `LoginView` 는 `RouterLink` 를 **import 하지 않는다** — 앱에서 라우터 플러그인이 전역 등록해
 * 주기 때문이다. 테스트에는 플러그인이 없으므로 **전역 컴포넌트로 넣어 준다**
 * (App.vue 는 명시적으로 import 해서 이 손질이 필요 없었다 — 같은 태그인데 해석 경로가 다르다).
 */
const GLOBAL = {
  components: { RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' } },
};
const mountView = () => mount(LoginView, { global: GLOBAL });

const signupTo = (w) => w.findAllComponents({ name: 'RouterLink' })
  .find((l) => l.text() === '회원가입')?.props('to');

/** ⚠ 아이디·비밀번호는 DevExtreme 이라 `change` 로 확정한다. 비밀번호 칸은 `mode="password"` 다. */
async function submit(w) {
  const inputs = w.findAll('input.dx-texteditor-input');
  await inputs[0].setValue('zzuser');
  await inputs[0].trigger('change');
  await inputs[1].setValue('Tulip-Harbor-72');
  await inputs[1].trigger('change');
  await w.findAll('button').find((b) => b.text().includes('로그인')).trigger('click');
  await flushPromises();
}

beforeEach(() => {
  push.mockReset();
  login.mockReset();
  login.mockResolvedValue({});
  route.query = {};
});
afterEach(() => wrapper?.unmount());

describe('LoginView — 복귀 경로 (J-3)', () => {
  it('🔴 「회원가입」 링크가 복귀 경로를 **물려준다** — 여기서 끊기면 가입한 사람만 길을 잃는다', () => {
    route.query = { redirect: '/products/p1' };
    wrapper = mountView();

    expect(signupTo(wrapper)).toEqual({ path: '/signup', query: { redirect: '/products/p1' } });
  });

  it('복귀 경로가 없으면 그대로 맨몸으로 넘긴다 (없는 값을 지어내지 않는다)', () => {
    wrapper = mountView();
    expect(signupTo(wrapper)).toEqual({ path: '/signup', query: {} });
  });

  it('로그인에 성공하면 **왔던 자리로** 돌아간다', async () => {
    route.query = { redirect: '/products/p1?tab=review' };
    wrapper = mountView();

    await submit(wrapper);

    expect(login).toHaveBeenCalled();
    expect(push).toHaveBeenLastCalledWith('/products/p1?tab=review');
  });

  it('복귀 경로가 없으면 홈으로 — 예전부터의 동작이라 J-3 이 바꾸지 않는다', async () => {
    wrapper = mountView();
    await submit(wrapper);
    expect(push).toHaveBeenLastCalledWith('/');
  });
});
