import { ref, watch } from 'vue';
import { authState } from './auth';

/**
 * 최근 검색어 (2026-09-01, BACKLOG G-7).
 *
 * <p>서버에 저장하지 않고 <b>localStorage</b>에 둔다 — {@code recentlyViewed} 와 <b>같은 판단</b>이다:
 * 개인화가 아니라 이 브라우저의 편의라 로그인 없이도 동작해야 하고(비회원도 검색한다),
 * 서버에 테이블·조회 API 를 더할 값어치가 없다.
 *
 * <p>⚠ <b>구조를 {@code recentlyViewed} 에서 그대로 가져왔다</b> — 계정별 키(비회원은 {@code guest}) ·
 * 상한 · 손상 시 빈 목록 · 계정 전환 시 {@code flush:'sync'} 교체. 🔴 <b>베낀 것이 아니라 같은 문제다</b>:
 * localStorage 는 브라우저 단위라 그냥 두면 <b>로그아웃 후 다른 계정에서 내 검색어가 보인다</b>
 * (계정 누수). 검색어는 «무엇을 찾고 있었나» 라서 본 상품보다 오히려 더 사적이다.
 *
 * <p>🔴 <b>인기 검색어는 만들지 않는다</b> — 검색 로그(Redis 카운터)가 필요한데
 * <b>지금 트래픽에선 «인기» 가 성립하지 않는다</b>(백로그 G-7 의 판단). 최근 검색어만 먼저 한다.
 *
 * <p>⚠ <b>«검색을 실행한 순간» 만 담는다</b>(2026-09-01 사용자 확정). 입구가 둘이라
 * {@link pushRecentSearch} 를 <b>둘이 함께 부른다</b> — 헤더 폼({@code App.vue})과
 * 목록 필터({@code ProductListView.apply}). 🔴 <b>{@code ?name=} 이 적용될 때마다 담지 않는다</b>:
 * 그러면 남이 공유한 링크를 열기만 해도 내 검색어가 된다.
 */
const PREFIX = 'glassvue.recentSearches';
const MAX = 8;

function keyFor(userId) {
  return `${PREFIX}.${userId || 'guest'}`;
}
function activeKey() {
  return keyFor(authState.user?.id);
}

/** 반응형 목록 — 최신이 앞. 현재 계정의 것을 들고 있는다(로그인 상태가 바뀌면 아래 watch 가 갈아끼운다). */
export const recentSearches = ref(load(activeKey()));

function load(key) {
  try {
    const raw = localStorage.getItem(key);
    const arr = raw ? JSON.parse(raw) : [];
    // ⚠ 문자열만 남긴다 — 손으로 고쳐진 값·옛 형식이 섞여도 화면이 안 깨지게.
    return Array.isArray(arr) ? arr.filter((t) => typeof t === 'string' && t) : [];
  } catch (e) {
    return []; // 손상·차단 시 빈 목록(기능이 죽지 않게)
  }
}

function save(list) {
  recentSearches.value = list;
  try {
    localStorage.setItem(activeKey(), JSON.stringify(list));
  } catch (e) {
    /* 용량 초과·프라이빗 모드 — 화면 상태는 이미 갱신했으니 조용히 넘어간다 */
  }
}

/**
 * 방금 검색한 말을 맨 앞에 넣는다. 최대 {@link MAX} 개, 넘치면 오래된 것부터 버린다.
 *
 * <p>⚠ <b>같은 말은 대소문자를 무시하고 하나로 본다</b> — «Zibar» 를 치고 «zibar» 를 치면
 * 목록에 둘이 남을 이유가 없다. 남기는 것은 <b>마지막에 친 표기</b>다(방금 친 것이 내 기억에 가깝다).
 * ⚠ 공백만 있는 말은 안 담는다 — 헤더 폼도 빈 검색을 «전체 목록» 으로 처리한다.
 */
export function pushRecentSearch(term) {
  const q = typeof term === 'string' ? term.trim() : '';
  if (!q) return;
  const lower = q.toLowerCase();
  save([q, ...recentSearches.value.filter((t) => t.toLowerCase() !== lower)].slice(0, MAX));
}

/** 한 줄만 지운다 — 오타로 남은 말을 목록 전체를 버리지 않고 치울 수 있어야 한다. */
export function removeRecentSearch(term) {
  save(recentSearches.value.filter((t) => t !== term));
}

/** 전체 지우기 — 남의 기기에서 검색했을 때 한 번에 치우는 길(계정별 키가 있어도 같은 계정이면 남는다). */
export function clearRecentSearches() {
  save([]);
}

/**
 * 🔴 <b>비회원으로 친 검색어를 이 계정으로 옮긴다 — «가입할 때만» 부른다</b> (2026-09-01, BACKLOG J-5).
 *
 * <p>⚠ <b>«검색어도 옮긴다» 는 결정이 필요했다</b> — 검색어는 «무엇을 찾고 있었나» 라서 본 상품보다
 * 사적이고, 이 파일이 계정별 키를 고른 이유가 그것이다. 그런데도 옮기기로 한 것은
 * <b>①가입 시점 한정이라 귀속이 명확하고 ②옮긴 뒤 guest 를 비워 오히려 노출이 줄기</b> 때문이다
 * (사용자 결정 2026-09-01). 🔴 <b>규칙을 둘로 가르지 않은 것도 이유다</b> — «본 상품은 옮기는데
 * 검색어만 사라진다» 는 화면에서 설명할 길이 없다.
 *
 * <p>자세한 판단은 {@code recentlyViewed.adoptGuestRecentlyViewed} 주석과 같다.
 */
export function adoptGuestRecentSearches() {
  const key = activeKey();
  if (key === keyFor(null)) return;
  const guest = load(keyFor(null));
  if (!guest.length) return;
  // ⚠ 중복 판정은 이 파일의 규칙(대소문자 무시)을 그대로 쓴다 — 여기서 다시 정하지 않는다.
  const mine = recentSearches.value;
  const lower = mine.map((t) => t.toLowerCase());
  const merged = [...mine, ...guest.filter((g) => !lower.includes(g.toLowerCase()))].slice(0, MAX);
  recentSearches.value = merged;
  try {
    localStorage.setItem(key, JSON.stringify(merged));
    localStorage.removeItem(keyFor(null));      // 🔴 이동이라 원본을 지운다
  } catch (e) {
    /* 저장 실패 — 화면 상태는 이미 갱신했다 */
  }
}

// 로그인·로그아웃·계정 전환 시 그 계정의 목록으로 갈아끼운다 — 브라우저에 남아 있어도 계정끼리 안 섞인다.
// flush:'sync' 인 이유는 recentlyViewed 와 같다: 전환 직후~렌더 사이에 push 가 끼면 이전(guest) 목록에
// 얹혀 새 계정 키에 섞일 여지가 있다. 전환은 드물어 동기 비용이 무의미하다.
// (앱 수명 동안 사는 싱글턴 스토어라 watch 를 정리하지 않아도 된다.)
watch(() => authState.user?.id, () => {
  recentSearches.value = load(activeKey());
}, { flush: 'sync' });
