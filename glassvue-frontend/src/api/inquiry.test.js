import { describe, it, expect, beforeEach, vi } from 'vitest';
import { fetchAdminInquiries, answerInquiry, INQUIRY_STATUS_OPTIONS } from './inquiry';
import { clearSession } from '../stores/auth';

/**
 * 관리자 문의 목록 (2026-08-06, G-3 1단계).
 *
 * ⚠ 여기서 지키는 것은 **「전체」가 파라미터를 빼는 것** 하나다. `'ALL'` 같은 문자열을 지어 보내면
 * 서버가 enum 변환에 실패해 **400** 이고, 화면에는 그게 **"문의가 없다"** 로 보인다 —
 * 관리자 주문(`?status`)·리뷰(`?hidden`)에서 이미 두 번 나온 자리다.
 */
describe('관리자 문의 목록 파라미터 (G-3)', () => {
  const okRes = { ok: true, status: 200, json: () => Promise.resolve({ success: true, data: null }) };

  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  async function callOf(arg) {
    const fetchMock = vi.fn().mockResolvedValue(okRes);
    global.fetch = fetchMock;
    await fetchAdminInquiries(arg);
    return fetchMock.mock.calls[0][0];
  }

  it('상품 경로가 아니라 **관리자 경로**로 간다(상품 상세를 거치지 않는 것이 이 기능이다)', async () => {
    expect((await callOf()).pathname).toBe('/api/admin/inquiries');
  });

  it('status 를 주면 그대로 보낸다', async () => {
    expect((await callOf({ status: 'WAITING' })).searchParams.get('status')).toBe('WAITING');
    expect((await callOf({ status: 'ANSWERED' })).searchParams.get('status')).toBe('ANSWERED');
  });

  it('🔴 status 가 null 이면(=전체) 파라미터가 **아예 빠진다**', async () => {
    expect((await callOf({ status: null })).searchParams.has('status')).toBe(false);
    expect((await callOf()).searchParams.has('status')).toBe(false);
  });

  it('선택지의 「전체」는 반드시 null 이다(문자열이면 400 → 화면엔 "없다"로 보인다)', () => {
    const all = INQUIRY_STATUS_OPTIONS.find((o) => o.text === '전체');
    expect(all.value).toBeNull();
    // ⚠ 기본값은 **첫 항목**이 아니라 화면이 정한다. 다만 선택지에 둘 다 있어야 고를 수 있다.
    expect(INQUIRY_STATUS_OPTIONS.map((o) => o.value)).toContain('WAITING');
    expect(INQUIRY_STATUS_OPTIONS.map((o) => o.value)).toContain('ANSWERED');
  });
});

describe('답변은 기존 API 를 그대로 쓴다 (G-3)', () => {
  const okRes = { ok: true, status: 200, json: () => Promise.resolve({ success: true, data: null }) };

  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  it('🔴 답변은 **관리자 경로가 아니라** 기존 `/api/inquiries/{id}/answer` 로 POST 한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okRes);
    global.fetch = fetchMock;

    await answerInquiry('q-1', '내일 출고됩니다');

    // 관리자 목록을 새로 만들면서 답변 경로까지 새로 판 게 아니라는 것 — 새로 는 건 «찾는 길» 뿐이다.
    expect(fetchMock.mock.calls[0][0].pathname).toBe('/api/inquiries/q-1/answer');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ answer: '내일 출고됩니다' });
  });
});
