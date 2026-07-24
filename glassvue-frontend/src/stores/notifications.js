import { reactive, ref } from 'vue';
import { authState } from './auth';
import {
  fetchNotifications,
  fetchUnreadCount,
  markNotificationRead,
  markAllNotificationsRead,
} from '../api/notification';

/**
 * 인앱 알림 상태 + 실시간 스트림 (2026-07-24, SSE).
 *
 * <p>실시간은 SSE 인데, 브라우저 기본 {@code EventSource} 는 Authorization 헤더를 못 실어서
 * 우리 JWT(로컬스토리지) 방식과 안 맞는다. 그래서 <b>fetch + ReadableStream</b> 으로 직접 열고
 * Bearer 헤더를 실어 붙인다(토큰을 URL 에 노출하지 않는다). 끊기면 백오프로 재연결한다.
 */
const state = reactive({
  items: [],
  unread: 0,
  loaded: false,
});
export const notificationState = state;

/** Toaster 가 지켜보는 "가장 최근 도착한 알림". 새 알림이 오면 여기에 실어 토스트를 띄운다. */
export const latestToast = ref(null);

let controller = null;
let stopped = false;
let backoff = 1000;

export async function loadUnread() {
  if (!authState.access) return;
  try {
    state.unread = await fetchUnreadCount();
  } catch (e) {
    /* 비로그인·일시 오류 — 뱃지는 0으로 둔다 */
  }
}

export async function loadRecent() {
  if (!authState.access) return;
  try {
    const page = await fetchNotifications({ size: 20 });
    state.items = page.content;
    state.loaded = true;
  } catch (e) {
    /* 목록 로드 실패해도 벨은 동작 */
  }
}

function onIncoming(n) {
  if (!state.items.some((x) => x.id === n.id)) {
    state.items = [n, ...state.items].slice(0, 50);
  }
  state.unread += 1;
  latestToast.value = n; // 토스트 트리거(Toaster 가 watch)
}

/** 로그인 상태에서 스트림을 연다. 이미 열려 있으면 중복 연결하지 않는다. */
export function connectNotifications() {
  if (!authState.access || controller) return;
  stopped = false;
  loadUnread();
  openStream();
}

async function openStream() {
  controller = new AbortController();
  try {
    const res = await fetch('/api/notifications/stream', {
      headers: { Accept: 'text/event-stream', Authorization: 'Bearer ' + authState.access },
      signal: controller.signal,
    });
    if (!res.ok || !res.body) throw new Error('stream ' + res.status);
    backoff = 1000; // 성공적으로 붙었으면 백오프 리셋

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    // SSE 프레임은 빈 줄(\n\n)로 구분된다. 프레임 단위로 잘라 파싱한다.
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let idx;
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        handleFrame(buffer.slice(0, idx));
        buffer = buffer.slice(idx + 2);
      }
    }
  } catch (e) {
    /* abort(정상 종료) 또는 네트워크 오류 — 아래 재연결로 처리 */
  }
  controller = null;
  if (!stopped) {
    // 끊기면 백오프 후 재연결. 토큰 만료로 401 이면 이 사이 REST 호출이 refresh 하고 다음 연결이 성공한다.
    setTimeout(() => {
      if (!stopped && authState.access) openStream();
    }, backoff);
    backoff = Math.min(backoff * 2, 30000);
  }
}

function handleFrame(frame) {
  let event = 'message';
  let data = '';
  for (const line of frame.split('\n')) {
    if (line.startsWith(':')) continue; // 하트비트 주석 — 무시
    if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) data += line.slice(5).trim();
  }
  if (event === 'notification' && data) {
    try {
      onIncoming(JSON.parse(data));
    } catch (e) {
      /* 잘못된 프레임 — 무시 */
    }
  }
}

/** 로그아웃 시 — 스트림을 닫고 상태를 비운다(다음 사용자에게 안 새게). */
export function disconnectNotifications() {
  stopped = true;
  if (controller) controller.abort();
  controller = null;
  state.items = [];
  state.unread = 0;
  state.loaded = false;
  latestToast.value = null;
}

/** 읽음 — 화면 먼저 반영(낙관적) 후 서버. */
export async function markRead(id) {
  const n = state.items.find((x) => x.id === id);
  if (n && !n.read) {
    n.read = true;
    state.unread = Math.max(0, state.unread - 1);
  }
  try {
    await markNotificationRead(id);
  } catch (e) {
    /* 실패해도 화면은 읽음 — 다음 조회에서 서버 상태로 보정된다 */
  }
}

export async function markAllRead() {
  state.items = state.items.map((n) => ({ ...n, read: true }));
  state.unread = 0;
  try {
    await markAllNotificationsRead();
  } catch (e) {
    /* 무시 — 다음 조회에서 보정 */
  }
}
