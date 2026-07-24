import { reactive } from 'vue';
import { fetchWishlistProductIds, addWishlist, removeWishlist } from '../api/wishlist';

/**
 * 찜한 상품 id 집합 (2026-07-24).
 *
 * 상품 목록·상세·찜 목록이 **같은 상태**를 봐야 한다. 화면마다 따로 들고 있으면
 * 상세에서 찜을 풀고 뒤로 갔을 때 목록의 하트가 여전히 채워져 있는 식으로 어긋난다.
 *
 * 서버 응답(`ProductResponse`)에 찜 여부가 없는 이유는 catalog가 wishlist를 알게 되면
 * 도메인 순환이 되기 때문이다 — 그 대신 화면이 이 집합을 들고 합친다.
 */
const state = reactive({
  ids: new Set(),
  loaded: false, // 로그인 세션당 한 번만 받아오면 되므로 중복 요청을 막는다
});

export const wishlistState = state;

export function isWishlisted(productId) {
  return state.ids.has(productId);
}

/**
 * 찜 id 집합을 서버에서 받아온다. 이미 받았으면 아무것도 하지 않는다.
 * 실패해도 조용히 넘어간다 — 하트가 빈 채로 보일 뿐이고, 상품을 보는 데 지장이 없어야 한다.
 */
export async function loadWishlistIds(force = false) {
  if (state.loaded && !force) return;
  try {
    const ids = await fetchWishlistProductIds();
    state.ids = new Set(ids);
    state.loaded = true;
  } catch (e) {
    /* 비로그인이거나 일시 오류 — 하트는 빈 상태로 둔다 */
  }
}

/**
 * 찜 토글. **화면을 먼저 바꾸고**(낙관적 갱신) 서버에 보낸다 — 하트는 즉시 반응해야 하는 UI다.
 * 실패하면 되돌린다. 서버가 추가·해제를 멱등으로 처리하므로 중복 클릭은 문제가 되지 않는다.
 *
 * @returns 토글 후 상태(true = 찜됨). 화면이 안내 문구를 고를 때 쓴다.
 */
export async function toggleWishlist(productId) {
  const was = state.ids.has(productId);
  if (was) state.ids.delete(productId);
  else state.ids.add(productId);

  try {
    await (was ? removeWishlist(productId) : addWishlist(productId));
    return !was;
  } catch (e) {
    if (was) state.ids.add(productId);
    else state.ids.delete(productId);
    throw e;
  }
}

/** 로그아웃 시 비운다 — 안 비우면 다음 사람이 남의 하트를 본다. */
export function clearWishlist() {
  state.ids = new Set();
  state.loaded = false;
}
