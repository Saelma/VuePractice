import { reactive } from 'vue';
import { fetchRestockProductIds, subscribeRestock, unsubscribeRestock } from '../api/restock';

/**
 * 재입고 신청한 상품 id 집합 (B-9). 위시리스트 스토어와 같은 구조·같은 이유다.
 *
 * ⚠ 위시리스트와 다른 점: 재입고 알림이 **발송되면 서버가 그 상품 신청을 지운다**(일회성).
 * 그러면 이 집합의 값이 서버와 어긋나는데, 화면 새로고침(재로그인 포함)이나 상품 상세 재진입 때
 * 다시 받아오므로 자연히 맞춰진다. 굳이 실시간으로 지우지 않는다(재입고 SSE 알림은 오지만
 * "어느 상품인지"만 알려줄 뿐, 이 집합을 즉시 손볼 만큼의 이득이 없다).
 */
const state = reactive({
  ids: new Set(),
  loaded: false, // 세션당 한 번만
});

export const restockState = state;

export function isRestockSubscribed(productId) {
  return state.ids.has(productId);
}

/** 신청 id 집합을 받아온다. 이미 받았으면 아무것도 안 한다. 실패는 조용히 넘긴다(버튼이 빈 상태일 뿐). */
export async function loadRestockIds(force = false) {
  if (state.loaded && !force) return;
  try {
    const ids = await fetchRestockProductIds();
    state.ids = new Set(ids);
    state.loaded = true;
  } catch (e) {
    /* 비로그인이거나 일시 오류 — 버튼은 "신청" 상태로 둔다 */
  }
}

/**
 * 신청 토글. 화면을 먼저 바꾸고(낙관적) 서버에 보낸다. 실패하면 되돌린다.
 * 서버가 멱등이라 중복 클릭은 문제되지 않는다.
 * @returns 토글 후 상태(true = 신청됨). 안내 문구를 고를 때 쓴다.
 */
export async function toggleRestock(productId) {
  const was = state.ids.has(productId);
  if (was) state.ids.delete(productId);
  else state.ids.add(productId);

  try {
    await (was ? unsubscribeRestock(productId) : subscribeRestock(productId));
    return !was;
  } catch (e) {
    if (was) state.ids.add(productId);
    else state.ids.delete(productId);
    throw e;
  }
}

/** 로그아웃 시 비운다 — 안 비우면 다음 사람이 남의 신청 상태를 본다(위시리스트와 같은 처리). */
export function clearRestock() {
  state.ids = new Set();
  state.loaded = false;
}
