// 공통 API 클라이언트.
// 백엔드는 항상 ApiResponse<T> 래퍼로 응답한다:
//   성공: { success: true,  data: ... }
//   에러: { success: false, error: { code, message } }
// 여기서 래퍼를 벗겨 data만 돌려주고, 실패면 message로 예외를 던진다.

async function request(path, { method = 'GET', params, body } = {}) {
  const url = new URL(path, window.location.origin);
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, value);
      }
    }
  }

  const options = { method, headers: { Accept: 'application/json' } };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }

  const res = await fetch(url, options);
  const payload = await res.json().catch(() => null);

  if (!res.ok || !payload || payload.success !== true) {
    const message = payload?.error?.message || `요청 실패 (HTTP ${res.status})`;
    throw new Error(message);
  }
  return payload.data;
}

export const apiGet = (path, params) => request(path, { method: 'GET', params });
export const apiPost = (path, body) => request(path, { method: 'POST', body });
export const apiPut = (path, body) => request(path, { method: 'PUT', body });
export const apiDelete = (path) => request(path, { method: 'DELETE' });
