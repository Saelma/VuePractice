// 공통 API 클라이언트.
// 백엔드는 항상 ApiResponse<T> 래퍼로 응답한다:
//   성공: { success: true,  data: ... }
//   에러: { success: false, error: { code, message } }
// 여기서 래퍼를 벗겨 data만 돌려주고, 실패면 message로 예외를 던진다.

async function request(path, { params, ...options } = {}) {
  const url = new URL(path, window.location.origin);
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, value);
      }
    }
  }

  const res = await fetch(url, {
    headers: { Accept: 'application/json', ...(options.headers || {}) },
    ...options,
  });

  const body = await res.json().catch(() => null);

  if (!res.ok || !body || body.success !== true) {
    const message = body?.error?.message || `요청 실패 (HTTP ${res.status})`;
    throw new Error(message);
  }
  return body.data;
}

export function apiGet(path, params) {
  return request(path, { method: 'GET', params });
}
