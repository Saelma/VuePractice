import { authState, setTokens, clearSession } from '../stores/auth';

// 백엔드는 항상 ApiResponse<T> 래퍼로 응답한다. 여기서 벗겨 data만 돌려주고 실패면 예외.
// 로그인 상태면 Authorization 헤더 자동 첨부, access 만료(401) 시 refresh로 1회 자동 재발급.

let refreshPromise = null;

async function doRefresh() {
  if (!authState.refresh) return false;
  try {
    const res = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ refreshToken: authState.refresh }),
    });
    const payload = await res.json().catch(() => null);
    if (res.ok && payload && payload.success) {
      setTokens(payload.data.accessToken, payload.data.refreshToken);
      return true;
    }
  } catch (e) {
    /* 네트워크 오류 → 실패 처리 */
  }
  return false;
}

function tryRefresh() {
  // 동시 401이 여러 개여도 refresh는 한 번만
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function request(path, { method = 'GET', params, body, _retried } = {}) {
  const url = new URL(path, window.location.origin);
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, value);
      }
    }
  }

  const options = { method, headers: { Accept: 'application/json' } };
  if (authState.access) {
    options.headers.Authorization = 'Bearer ' + authState.access;
  }
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }

  const res = await fetch(url, options);

  // access 만료 등 401 → refresh 1회 시도 후 원 요청 재시도
  if (res.status === 401 && !_retried && authState.refresh) {
    const ok = await tryRefresh();
    if (ok) {
      return request(path, { method, params, body, _retried: true });
    }
    clearSession(); // refresh도 실패 → 로그아웃 상태로
  }

  const payload = await res.json().catch(() => null);
  if (!res.ok || !payload || payload.success !== true) {
    const err = new Error(payload?.error?.message || `요청 실패 (HTTP ${res.status})`);
    err.status = res.status;
    err.code = payload?.error?.code;
    throw err;
  }
  return payload.data;
}

export const apiGet = (path, params) => request(path, { method: 'GET', params });
export const apiPost = (path, body) => request(path, { method: 'POST', body });
export const apiPut = (path, body) => request(path, { method: 'PUT', body });
export const apiPatch = (path, body) => request(path, { method: 'PATCH', body });
export const apiDelete = (path) => request(path, { method: 'DELETE' });

// 멀티파트 업로드 (FormData). Content-Type은 브라우저가 boundary와 함께 설정하도록 둔다.
export async function apiUpload(path, formData) {
  const options = { method: 'POST', headers: { Accept: 'application/json' }, body: formData };
  if (authState.access) {
    options.headers.Authorization = 'Bearer ' + authState.access;
  }
  const res = await fetch(new URL(path, window.location.origin), options);
  const payload = await res.json().catch(() => null);
  if (!res.ok || !payload || payload.success !== true) {
    const err = new Error(payload?.error?.message || `업로드 실패 (HTTP ${res.status})`);
    err.status = res.status;
    throw err;
  }
  return payload.data;
}
