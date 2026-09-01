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
    // ⚠ 세일 값도 함께 스냅샷한다 — 없으면 세일 중인 상품이 「그냥 싼 상품」으로 보인다
    //    (2026-08-19, G-5. 찜 목록과 같은 이유).
    // 🔴 **낡을 수 있다.** 이건 localStorage 스냅샷이라 세일이 끝나도 여기 값은 그대로다 —
    //    다만 그건 **가격 자체도 원래 그랬던 것**이라(관리자가 판매가를 바꿔도 안 따라온다)
    //    세일이 새로 만든 문제가 아니다. 클릭하면 상세에서 진짜 값을 본다.
    regularPrice: product.regularPrice ?? product.price,
    discountRate: product.discountRate ?? null,
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

/**
 * 🔴 <b>비회원으로 쌓은 것을 이 계정으로 옮긴다 — «가입할 때만» 부른다</b> (2026-09-01, BACKLOG J-5).
 *
 * <p>⚠ <b>자동으로 하지 않는 이유</b>: 로그인과 가입은 <b>같은 {@code login()} 을 부르므로</b>
 * 아래 {@code watch} 는 둘을 <b>구분할 수 없다.</b> 그래서 이관은 «스토어가 알아서» 가 아니라
 * <b>가입 화면이 명시적으로</b> 부르는 형태다(사용자 결정 2026-09-01).
 * 🔴 <b>로그인에서는 안 옮긴다</b> — 공용 PC 에서 앞사람이 둘러본 것이 다음 로그인한 사람 계정으로
 * 들어가면 <b>되돌릴 수 없다.</b> 가입은 «그 브라우저에서 방금 계정을 만든 사람» 이라 귀속이 가장 명확하다.
 *
 * <p>🔴 <b>옮긴 뒤 guest 키를 비운다(복사가 아니라 이동).</b> ⚠ 이게 <b>지금보다 안전하다</b> —
 * 지금은 guest 기록이 브라우저에 계속 남아 <b>다음 사람이 본다.</b>
 *
 * <p>⚠ <b>비로그인 상태에서 부르면 아무 일도 안 한다</b>(guest → guest 자기 자신 이동 방지).
 */
export function adoptGuestRecentlyViewed() {
  const key = activeKey();
  if (key === keyFor(null)) return;             // 아직 계정이 없다 — 옮길 곳이 없다
  const guest = load(keyFor(null));
  if (!guest.length) return;                    // 비었으면 계정 목록을 건드리지 않는다
  // 계정에 이미 있는 것이 앞(그게 더 최근이다). 같은 상품은 하나로, 상한을 넘으면 오래된 쪽이 밀린다.
  const mine = recentlyViewed.value;
  const merged = [...mine, ...guest.filter((g) => !mine.some((m) => m.id === g.id))].slice(0, MAX);
  recentlyViewed.value = merged;
  try {
    localStorage.setItem(key, JSON.stringify(merged));
    localStorage.removeItem(keyFor(null));      // 🔴 이동이라 원본을 지운다
  } catch (e) {
    /* 저장 실패 — 화면 상태는 이미 갱신했다 */
  }
}

// 로그인·로그아웃·계정 전환 시 그 계정의 목록으로 갈아끼운다 — 브라우저에 남아 있어도 계정끼리 안 섞인다.
// flush:'sync' 인 이유: 로그인 직후~렌더 사이에 pushRecentlyViewed 가 끼면 이전(guest) 목록에 얹혀
// 새 계정 키에 섞일 여지가 있다. 계정 전환은 드물어(로그인/로그아웃) 동기 비용이 무의미하고, 즉시 갈아끼우면 그 틈이 없다.
// (앱 수명 동안 사는 싱글턴 스토어라 watch 를 정리하지 않아도 된다.)
watch(() => authState.user?.id, () => {
  recentlyViewed.value = load(activeKey());
}, { flush: 'sync' });
