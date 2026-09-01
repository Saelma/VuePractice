import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

// 「로그인하러 가되 돌아올 자리를 들고 간다」 (2026-09-01, BACKLOG §J).
//
// 🔴 **이 규칙이 지켜지는지를 여기서 한 번에 본다.** 감사 전에는 같은 값을 다루는 자리가 다섯인데
//    셋만 지켰고(가드·찜·재입고 ✅ / 헤더·가입 링크 ❌), **손으로 적은 것이 셋이라 넷째가 안 적혔다.**
//    이제 규칙이 한 곳이므로 **여기가 무너지면 다섯이 함께 무너진다** — 그만큼 촘촘히 본다.
//
// ⚠ «어떻게 보이나» 는 여기서 안 본다(버튼이냐 링크냐는 부르는 쪽 몫이다).
//    부르는 쪽이 실제로 이걸 쓰는지는 App.test.js·LoginView.test.js 가 본다.

const push = vi.fn();
const route = { path: '/products/p1', fullPath: '/products/p1?tab=review', query: {} };
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  useRoute: () => route,
}));

import { useLoginRedirect } from './useLoginRedirect';

/** 컴포저블은 setup 안에서만 산다 — 최소 숙주를 세워 결과만 꺼낸다. */
function host() {
  let api;
  const wrapper = mount({
    setup() {
      api = useLoginRedirect();
      return () => null;
    },
  });
  return { api, wrapper };
}

beforeEach(() => {
  push.mockReset();
  route.path = '/products/p1';
  route.fullPath = '/products/p1?tab=review';
});

describe('useLoginRedirect', () => {
  it('🔴 지금 자리를 **fullPath 로** 들고 간다 — 쿼리까지 살아야 돌아왔을 때 같은 화면이다', () => {
    const { api } = host();
    expect(api.loginTo.value).toEqual({
      path: '/login', query: { redirect: '/products/p1?tab=review' },
    });
  });

  it('회원가입도 같은 값을 들고 간다', () => {
    const { api } = host();
    expect(api.signupTo.value).toEqual({
      path: '/signup', query: { redirect: '/products/p1?tab=review' },
    });
  });

  it('goLogin 은 그 목적지로 **밀어 준다** — 링크를 못 쓰는 자리(버튼)용이다', () => {
    const { api } = host();
    api.goLogin();
    expect(push).toHaveBeenCalledWith({
      path: '/login', query: { redirect: '/products/p1?tab=review' },
    });
  });

  it.each(['/login', '/signup', '/forgot-password', '/find-id', '/reset-password'])(
    '🔴 인증 화면(%s)에서는 복귀 경로를 **안 붙인다** — 자기 자신으로 도는 고리가 된다',
    (path) => {
      route.path = path;
      route.fullPath = path;
      const { api } = host();
      expect(api.loginTo.value).toEqual({ path: '/login' });
      expect(api.signupTo.value).toEqual({ path: '/signup' });
    },
  );

  it('⚠ 인증 화면이 아니면 경로가 바뀌어도 따라온다 (computed 라 굳지 않는다)', async () => {
    const { api } = host();
    expect(api.loginTo.value.query.redirect).toBe('/products/p1?tab=review');

    route.fullPath = '/cart';
    route.path = '/cart';
    // ⚠ 이 테스트가 쓰는 route 는 목이라 반응형이 아니다 — 새로 마운트해 «굳지 않았나» 만 본다.
    const again = host();
    expect(again.api.loginTo.value.query.redirect).toBe('/cart');
  });
});
