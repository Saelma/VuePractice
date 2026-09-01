import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

/**
 * 「로그인하러 가되 **돌아올 자리를 들고 간다**」 — 이 규칙을 한 곳에 둔다 (2026-09-01, BACKLOG §J).
 *
 * <p>🔴 <b>이 파일이 생긴 이유는 규칙이 없어서가 아니라 «여러 벌» 이어서다.</b> 감사 전에는 같은 값을
 * 다루는 자리가 다섯이었는데 **셋만 지켰다**:
 * <ul>
 *   <li>✅ 라우터 가드 — `{ path: '/login', query: { redirect: to.fullPath } }`</li>
 *   <li>✅ 찜 버튼 · ✅ 재입고 버튼 — 같은 모양을 <b>각자 손으로</b> 적어 뒀다</li>
 *   <li>❌ 헤더 「로그인」 — 쿼리가 없어 로그인하면 <b>홈으로 떨어졌다</b>(J-2)</li>
 *   <li>❌ 로그인 → 「회원가입」 — 넘어가는 순간 <b>경로를 버렸다</b>(J-3)</li>
 * </ul>
 * ⚠ <b>손으로 적은 것이 셋이면 넷째는 안 적힌다</b> — 그게 이 축 감사의 결론이었다.
 *
 * <p>⚠ <b>«어떻게 보이나» 는 여기 없다.</b> 상품 상세는 버튼(「로그인하고 담기」), 리뷰·문의는 문장 속
 * 링크, 헤더는 nav 링크다 — 모양은 부르는 쪽이 정하고 <b>여기는 «어디로 가나» 만</b> 갖는다
 * (`RecentSearches` 에서 위치를 부르는 쪽에 맡긴 것과 같은 판단).
 *
 * <p>🔴 <b>인증 화면에서는 복귀 경로를 안 붙인다</b> — `/login` 에서 붙이면 `redirect=/login` 이 되어
 * 로그인 후 <b>자기 자신으로 돌아오는 고리</b>가 된다. 가입·아이디 찾기·비밀번호 재설정도 같다.
 */
const AUTH_PATHS = ['/login', '/signup', '/forgot-password', '/find-id', '/reset-password'];

export function useLoginRedirect() {
  const route = useRoute();
  const router = useRouter();

  /** 지금 자리를 들고 가는 목적지를 만든다. 인증 화면이면 맨몸으로 보낸다(위 🔴 참조). */
  const to = (path) => (AUTH_PATHS.includes(route.path)
    ? { path }
    : { path, query: { redirect: route.fullPath } });

  const loginTo = computed(() => to('/login'));
  const signupTo = computed(() => to('/signup'));

  /** 버튼처럼 «눌러서» 가는 자리용. `RouterLink` 를 쓸 수 있으면 {@link loginTo} 가 낫다. */
  function goLogin() {
    router.push(loginTo.value);
  }

  return { loginTo, signupTo, goLogin };
}
