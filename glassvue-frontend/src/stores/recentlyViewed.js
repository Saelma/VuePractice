import { ref, watch } from 'vue';
import { authState } from './auth';

/**
 * 최근 본 상품 (2026-07-24, B-8).
 *
 * <p>서버에 저장하지 않고 <b>localStorage</b>에 둔다 — 개인화가 아니라 이 브라우저의 편의라
 * 로그인 없이도 동작해야 하고(비회원도 둘러본다), 서버에 테이블·조회 API 를 더할 값어치가 없다.
 * 무신사·쿠팡의 "최근 본" 도 대개 이 방식이다.
 *
 * <p>⚠ localStorage 는 <b>브라우저 단위</b>라 그냥 두면 로그아웃 후 다른 계정으로 들어가도 목록이
 * 그대로 남는다(계정 누수). 그래서 <b>키를 계정별로 나눈다</b> — {@code ...recentlyViewed.<계정id|guest>}.
 * 계정마다 자기 목록을 갖고, 비회원 브라우징도 guest 키로 따로 남으며, 재로그인해도 보존된다.
 * (기기 간 동기화까지는 안 한다 — 그건 DB 가 필요한 별개 기능이고, 편의 기능엔 과하다. 사용자 결정 2026-07-24.)
 *
 * <p>가격·이름까지 <b>스냅샷</b>으로 담는다 — 홈에서 다시 그릴 때 상품마다 서버를 되묻지 않으려는 것
 * (id 만 담으면 N번 조회가 필요하다). 대신 가격이 바뀌면 살짝 낡을 수 있는데, "최근 본" 은 바로가기지
 * 결제 근거가 아니라 허용된다(담기·주문은 언제나 상세에서 최신값으로 다시 한다).
 */
const PREFIX = 'glassvue.recentlyViewed';
const MAX = 10;

function keyFor(userId) {
  return `${PREFIX}.${userId || 'guest'}`;
}
function activeKey() {
  return keyFor(authState.user?.id);
}

/** 반응형 목록 — 현재 계정의 것을 들고 있는다. 로그인 상태가 바뀌면 아래 watch 가 갈아끼운다. */
export const recentlyViewed = ref(load(activeKey()));

function load(key) {
  try {
    const raw = localStorage.getItem(key);
    const arr = raw ? JSON.parse(raw) : [];
    return Array.isArray(arr) ? arr : [];
  } catch (e) {
    return []; // 손상·차단 시 빈 목록(기능이 죽지 않게)
  }
}

/**
 * 방금 본 상품을 맨 앞에 넣는다. 같은 상품은 중복 제거하고 최신 위치로 끌어올린다(최대 {@link MAX}, FIFO).
 * {@code product} 는 ProductResponse — 필요한 필드만 스냅샷한다. 현재 계정 키에 저장한다.
 */
export function pushRecentlyViewed(product) {
  if (!product?.id) return;
  const snapshot = {
    id: product.id,
    name: product.name,
    price: product.price,
    listPrice: product.listPrice ?? null,
    thumbUrl: product.images?.length ? product.images[0].thumbUrl : null,
  };
  const next = [snapshot, ...recentlyViewed.value.filter((p) => p.id !== product.id)].slice(0, MAX);
  recentlyViewed.value = next;
  try {
    localStorage.setItem(activeKey(), JSON.stringify(next));
  } catch (e) {
    /* 용량 초과·프라이빗 모드 — 화면 상태는 이미 갱신했으니 조용히 넘어간다 */
  }
}

// 로그인·로그아웃·계정 전환 시 그 계정의 목록으로 갈아끼운다 — 브라우저에 남아 있어도 계정끼리 안 섞인다.
// flush:'sync' 인 이유: 로그인 직후~렌더 사이에 pushRecentlyViewed 가 끼면 이전(guest) 목록에 얹혀
// 새 계정 키에 섞일 여지가 있다. 계정 전환은 드물어(로그인/로그아웃) 동기 비용이 무의미하고, 즉시 갈아끼우면 그 틈이 없다.
// (앱 수명 동안 사는 싱글턴 스토어라 watch 를 정리하지 않아도 된다.)
watch(() => authState.user?.id, () => {
  recentlyViewed.value = load(activeKey());
}, { flush: 'sync' });
