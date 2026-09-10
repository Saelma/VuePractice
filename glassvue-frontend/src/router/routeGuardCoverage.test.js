import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

// 라우트가 전부 **의도적으로** 분류돼 있는지 센다 — 경로 하나하나가 아니라 **덮개**를 지킨다.
//
// 🔴 **가드의 기본이 «통과»다.** `beforeEach` 는 `requiresAuth|requiresAdmin|requiresSuperAdmin`
//    중 하나가 있을 때만 막고, **아무것도 없으면 그냥 보낸다.** 즉 라우트를 새로 만들면서 meta 를
//    안 적으면 **아무도 안 묻고 공개된다** — 빌드도 테스트도 조용하다.
//    ⚠ `SecurityConfig` 의 `.anyRequest().permitAll()` 과 **정확히 같은 모양**이고,
//    백엔드는 `EndpointAuthCoverageTest` 로 그것을 막았다(2026-09-07, O-1). 이건 그 프론트 짝이다.
//
// 🔴 **라우트 목록을 «소스에서 긁지» 않고 라우터에서 꺼낸다.** 2026-09-10 에 정규식으로 세다가
//    **여러 줄로 쓴 라우트 하나(`/admin/coupons/calendar`)를 놓쳤다.** 세는 도구가 범위를 잘못 잡으면
//    조용히 넘어간다 — 실제 라우터가 유일하게 안 틀리는 목록이다.
//
// **고치는 법**: 빨개지면 둘 중 하나다 —
//   ①정말 공개할 화면이다 → `PUBLIC` 에 **이유와 함께** 추가한다.
//   ②아니다 → 라우트에 `meta` 를 붙인다. **어느 쪽이든 «결정» 이 남는다** — 그게 목적이다.

import router from './index';
import { setTokens, setUser, clearSession } from '../stores/auth';

/**
 * **비로그인에게 열려 있어도 되는 화면** — 2026-09-10 에 하나씩 판단했다.
 * ⚠ «지금 열려 있다» 가 아니라 «열려 있어야 한다» 는 목록이다.
 */
const PUBLIC = new Set([
  '/',                    // 홈 — 비회원이 둘러보는 첫 화면(J 축이 그 동선을 다뤘다)
  '/login',               // 로그인 전에 열려야 로그인을 한다
  '/signup',
  '/forgot-password',
  '/find-id',
  '/reset-password',
  '/terms',               // 약관·개인정보는 가입 «전에» 읽는 것이다
  '/privacy',
  '/notices',             // 공지는 비회원도 읽는다(쓰기만 관리자 — /notices/new 는 requiresAdmin)
  '/notices/:id',
  '/products',            // 카탈로그
  '/products/:id',
  '/mock-tracking',       // 택배사 조회를 흉내 낸 화면. 실제 택배사 페이지도 공개다
]);

/**
 * ⚠ **redirect 라우트는 «화면» 이 아니다** — 도착하지 않고 넘긴다.
 * 🔴 그래서 «자기 자리에 도착했나» 로 판정할 수 없다. 대신 **넘긴 곳에서 가드가 다시 돈다** —
 * 보호된 곳으로 넘기면 거기서 막히므로 이 라우트로는 아무것도 새지 않는다.
 * (404 라우트의 실제 규칙은 아래 별도 시험이 고정한다.)
 */
const isRedirect = (r) => typeof r.redirect !== 'undefined';

/** `:param` 을 실제 값으로 바꾼다. 가드는 값을 안 보지만 라우터가 매칭하려면 필요하다. */
function concrete(path) {
  if (path === '/:pathMatch(.*)*') return '/이-주소는-없다';
  return path.replace(/:[A-Za-z]+\([^)]*\)\*?/g, 'x').replace(/:[A-Za-z]+/g, '00000000-0000-7000-8000-000000000000');
}

async function go(path) {
  try {
    await router.push(path);
  } catch {
    /* 리다이렉트는 예외가 아니지만, 중복 네비게이션은 던질 수 있다 */
  }
  await router.isReady();
  return router.currentRoute.value;
}

const screens = () => router.getRoutes().filter((r) => !isRedirect(r));
const paths = () => screens().map((r) => r.path);

describe('라우터 가드 덮개', () => {
  beforeEach(async () => {
    clearSession();
    await go('/');
  });
  afterEach(() => clearSession());

  it('라우트 목록을 라우터에서 꺼낸다 — 0개면 «안 돌았음» 이다', () => {
    expect(router.getRoutes().length).toBeGreaterThan(30);
    expect(paths().length).toBeGreaterThan(30);
  });

  // 🔴 404 는 화면이 아니라 **redirect** 다. 규칙이 주석에만 있으면 조용히 바뀌므로 여기 고정한다:
  //    「`/admin/*` 오타는 관리 화면 «존재» 를 노출하지 않게 /products, 그 외는 홈으로」.
  it('⚠ 없는 주소: /admin 오타는 상품 목록으로, 그 밖은 홈으로', async () => {
    expect((await go('/admin/member')).path).toBe('/products');   // members 오타
    expect((await go('/없는-주소')).path).toBe('/');
  });

  it('🔴 비로그인: 모든 화면이 «막히거나» «의도적 공개» 둘 중 하나다', async () => {
    const leaked = [];
    const stale = [];

    for (const path of paths()) {
      const landed = await go(concrete(path));
      const passed = landed.matched.some((m) => m.path === path);

      if (PUBLIC.has(path) && !passed) {
        stale.push(`${path} → ${landed.path} (공개라고 적어 뒀는데 막힌다)`);
      } else if (!PUBLIC.has(path) && passed) {
        leaked.push(`${path} (meta 가 없어 비로그인에 열렸다)`);
      }
    }

    expect(leaked, [
      '비로그인에 열려 있는데 «공개» 라고 선언되지 않은 화면이다.',
      '가드의 기본이 «통과» 라 meta 를 빠뜨리면 이렇게 조용히 열린다.',
      '→ 공개가 맞으면 PUBLIC 에 이유와 함께 넣고, 아니면 라우트에 meta 를 붙일 것.',
    ].join('\n')).toEqual([]);
    expect(stale, 'PUBLIC 목록이 낡았다 — 막히도록 바뀐 화면이 아직 공개로 적혀 있다').toEqual([]);
  });

  it('🔴 일반 회원: 관리자 화면은 «로그인 유도 없이» 상품 목록으로 보낸다', async () => {
    setTokens('a', 'r');
    setUser({ id: 'u', role: 'USER' });

    for (const path of paths().filter((p) => p.startsWith('/admin'))) {
      const landed = await go(concrete(path));
      expect(landed.path, `${path} 는 일반 회원에게 열리면 안 된다`).toBe('/products');
    }
  });

  it('🔴 일반 ADMIN 은 감사 이력을 못 본다 — SUPER 전용이다', async () => {
    setTokens('a', 'r');
    setUser({ id: 'u', role: 'ADMIN' });

    expect((await go('/admin/audit')).path).toBe('/products');
    // ⚠ 대조군 — 같은 계정이 다른 관리자 화면은 연다. 안 그러면 «전부 막힌 것» 과 구별이 안 된다.
    expect((await go('/admin/orders')).path).toBe('/admin/orders');
  });

  it('⚠ 로그인 필요 화면은 «돌아갈 곳» 을 들고 로그인으로 보낸다', async () => {
    const landed = await go('/orders');
    expect(landed.path).toBe('/login');
    expect(landed.query.redirect).toBe('/orders');
  });
});
