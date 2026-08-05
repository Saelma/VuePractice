import { reactive, ref } from 'vue';
import { authState } from './auth';
import { refreshSession } from '../api/client';
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

async function openStream(afterRefresh = false) {
  controller = new AbortController();
  let status = 0;
  try {
    const res = await fetch('/api/notifications/stream', {
      headers: { Accept: 'text/event-stream', Authorization: 'Bearer ' + authState.access },
      signal: controller.signal,
    });
    status = res.status;
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
  if (stopped) return;

  // --- 401 = 토큰 만료. 여기서 직접 갱신한다 (2026-08-05) ---
  //
  // ⚠ 전에는 *"이 사이 REST 호출이 refresh 하고 다음 연결이 성공한다"* 고 **다른 요청에 기댔는데**,
  // 가만히 둔 탭에는 그 REST 호출이 없다. 그래서 백오프 상한(30초)에 얹혀 **31초마다 영원히 401** 을
  // 맞았다(실측 2026-08-04: 200 7건 · 401 108건). 백오프도 갱신 로직도 이미 있었고,
  // **스트림이 그 갱신 경로를 안 지나간 것**이 원인이었다.
  if (status === 401) {
    // 갱신하고도 또 401 이면 더 두드리지 않는다 — 뜨거운 재시도 루프를 만들지 않기 위한 가드.
    if (afterRefresh) {
      stopped = true;
      return;
    }
    const ok = await refreshSession();
    if (!ok) {
      // ⚠ 여기서 **세션은 건드리지 않는다**(사용자 결정, 2026-08-05). REST 의 `request()` 는
      // refresh 실패 시 clearSession() 하지만, 그건 **사용자가 방금 뭔가 눌렀을 때**의 이야기다.
      // 이 루프는 조작 없이 30초마다 도는 배경 작업이라, 네트워크 일시 장애 한 번이
      // **가만히 있던 사용자를 튕겨내는** 일이 된다. 스트림만 멈추고 세션의 운명은
      // 다음 사용자 조작(=REST 경로)이 정한다.
      stopped = true;
      return;
    }
    backoff = 1000;
    if (authState.access) openStream(true);
    return;
  }

  // 그 밖의 끊김(정상 타임아웃·네트워크) — 백오프 후 재연결
  setTimeout(() => {
    if (!stopped && authState.access) openStream();
  }, backoff);
  backoff = Math.min(backoff * 2, 30000);
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
