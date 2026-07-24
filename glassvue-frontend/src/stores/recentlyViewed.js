import { ref } from 'vue';

/**
 * 최근 본 상품 (2026-07-24, B-8).
 *
 * <p>서버에 저장하지 않고 <b>localStorage</b>에 둔다 — 개인화가 아니라 이 브라우저의 편의라
 * 로그인 없이도 동작해야 하고(비회원도 둘러본다), 서버에 테이블·조회 API 를 더할 값어치가 없다.
 * 무신사·쿠팡의 "최근 본" 도 대개 이 방식이다.
 *
 * <p>가격·이름까지 <b>스냅샷</b>으로 담는다 — 홈에서 다시 그릴 때 상품마다 서버를 되묻지 않으려는 것
 * (id 만 담으면 N번 조회가 필요하다). 대신 가격이 바뀌면 살짝 낡을 수 있는데, "최근 본" 은 바로가기지
 * 결제 근거가 아니라 허용된다(담기·주문은 언제나 상세에서 최신값으로 다시 한다).
 */
const KEY = 'glassvue.recentlyViewed';
const MAX = 8;

/** 반응형 목록 — 상세를 보면 홈(다음 방문)·같은 세션의 다른 탭 컴포넌트가 함께 갱신되게. */
export const recentlyViewed = ref(load());

function load() {
  try {
    const raw = localStorage.getItem(KEY);
    const arr = raw ? JSON.parse(raw) : [];
    return Array.isArray(arr) ? arr : [];
  } catch (e) {
    return []; // 손상·차단 시 빈 목록(기능이 죽지 않게)
  }
}

/**
 * 방금 본 상품을 맨 앞에 넣는다. 같은 상품은 중복 제거하고 최신 위치로 끌어올린다.
 * {@code product} 는 ProductResponse — 필요한 필드만 스냅샷한다.
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
    localStorage.setItem(KEY, JSON.stringify(next));
  } catch (e) {
    /* 용량 초과·프라이빗 모드 — 화면 상태는 이미 갱신했으니 조용히 넘어간다 */
  }
}
