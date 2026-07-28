import { reactive } from 'vue';
import { getCart } from '../api/cart';

/**
 * 장바구니 담긴 수량 (2026-07-28). 헤더 🛒 아이콘의 배지 하나를 위해 **개수만** 반응형으로 든다
 * (전체 장바구니 상태는 CartView 가 직접 조회 — 여기선 뱃지용 total 만).
 *
 * 갱신 지점: 로그인/마운트 시 로드, 담기(상품·홈·찜) 후 새로고침, CartView 의 수량변경·삭제·비우기가
 * load() 끝에 개수를 밀어 넣는다. 로그아웃 시 비운다(다음 사람에게 안 새게).
 */
const state = reactive({
  count: 0, // 서버 응답 totalQuantity(수량 합계)
  loaded: false, // 세션당 한 번만 받으면 되므로 중복 요청을 막는다
});

export const cartState = state;

/** CartView 처럼 이미 전체 장바구니를 받은 곳이 개수만 동기화할 때. */
export function setCartCount(count) {
  state.count = count || 0;
  state.loaded = true;
}

/**
 * 서버에서 장바구니 총수량을 받아 배지를 맞춘다. 이미 받았으면(그리고 force 아니면) 아무것도 안 한다.
 * 실패해도 조용히 넘어간다 — 비로그인이면 배지가 안 보일 뿐, 화면엔 지장이 없어야 한다.
 */
export async function loadCartCount(force = false) {
  if (state.loaded && !force) return;
  try {
    const cart = await getCart();
    setCartCount(cart.totalQuantity);
  } catch (e) {
    /* 비로그인이거나 일시 오류 — 배지는 안 보이는 채로 둔다 */
  }
}

/** 로그아웃 시 비운다. */
export function clearCartCount() {
  state.count = 0;
  state.loaded = false;
}
