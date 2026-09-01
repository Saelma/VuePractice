import { adoptGuestRecentlyViewed } from './recentlyViewed';
import { adoptGuestRecentSearches } from './recentSearches';

/**
 * 비회원으로 쌓은 기록을 방금 만든 계정으로 옮긴다 (2026-09-01, BACKLOG J-5).
 *
 * <p>🔴 <b>부르는 자리는 «가입» 한 곳뿐이다</b>({@code SignupView}). 로그인에서는 안 부른다 —
 * 공용 PC 에서 앞사람이 둘러본 것이 다음 로그인한 사람 계정으로 들어가면 <b>되돌릴 수 없다.</b>
 * ⚠ 로그인·가입이 <b>같은 {@code login()}</b> 을 부르므로 스토어의 {@code watch} 로는 둘을 못 가른다 —
 * 그래서 «가입 시에만» 은 <b>명시적 호출로만</b> 만들 수 있다(그게 이 파일이 있는 이유다).
 *
 * <p>⚠ <b>여기서 «무엇을 어떻게 합치나» 를 정하지 않는다.</b> 최근 본 상품은 상품 id 로,
 * 검색어는 대소문자를 무시하고 중복을 거른다 — 각자의 규칙이라 <b>각 스토어가 갖는다.</b>
 * 이 파일은 «가입 때 둘 다 옮긴다» 는 <b>정책</b>만 갖는다.
 *
 * <p>🔴 <b>둘 중 하나가 실패해도 나머지는 옮긴다</b> — 편의 기능이라 «다 되거나 다 안 되거나» 로
 * 묶을 값어치가 없다(각 스토어가 자기 try/catch 안에서 조용히 넘어간다).
 */
export function adoptGuestHistory() {
  adoptGuestRecentlyViewed();
  adoptGuestRecentSearches();
}
