import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  fetchAdminReviews, hideReview, unhideReview, REVIEW_HIDDEN_OPTIONS,
} from './review';
import { clearSession } from '../stores/auth';

/**
 * 관리자 리뷰 관리 (2026-08-04, B-18).
 *
 * ⚠ 여기서 지키는 것은 하나다 — **`hidden` 은 세 가지 상태**이고 그중 `false` 가
 * "안 보냄" 으로 눌리면 안 된다. 눌리면 「보이는 것만」 필터가 조용히 「전체」가 되는데,
 * 화면은 멀쩡히 뜨고 줄만 더 많아서 **아무도 못 알아챈다.**
 */
describe('관리자 리뷰 목록 파라미터 (B-18)', () => {
  const okRes = { ok: true, status: 200, json: () => Promise.resolve({ success: true, data: null }) };

  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  async function paramsOf(arg) {
    const fetchMock = vi.fn().mockResolvedValue(okRes);
    global.fetch = fetchMock;
    await fetchAdminReviews(arg);
    return fetchMock.mock.calls[0][0].searchParams;
  }

  it('🔴 hidden=false 는 **실제로 보낸다**(falsy 라고 빠지면 필터가 죽는다)', async () => {
    expect((await paramsOf({ hidden: false })).get('hidden')).toBe('false');
  });

  it('hidden=true 도 보낸다', async () => {
    expect((await paramsOf({ hidden: true })).get('hidden')).toBe('true');
  });

  it('hidden 을 안 주면(=전체) 파라미터가 아예 빠진다', async () => {
    expect((await paramsOf({})).has('hidden')).toBe(false);
    expect((await paramsOf()).has('hidden')).toBe(false);
  });

  it('선택지는 전체(null)·보이는 것만(false)·숨긴 것만(true) 세 가지다', () => {
    expect(REVIEW_HIDDEN_OPTIONS.map((o) => o.value)).toEqual([null, false, true]);
    // ⚠ '전체'는 반드시 null 이어야 파라미터가 빠진다 — 'all' 같은 문자열이면 서버가 400 을 내고
    //    화면에는 그게 "리뷰가 없다" 로 보인다.
    expect(REVIEW_HIDDEN_OPTIONS[0].value).toBeNull();
  });
});

describe('리뷰 숨김·해제 요청 (B-18)', () => {
  const okRes = { ok: true, status: 200, json: () => Promise.resolve({ success: true, data: null }) };

  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  it('숨김·해제는 서로 다른 경로로 POST 한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okRes);
    global.fetch = fetchMock;

    await hideReview('r-1');
    await unhideReview('r-1');

    expect(fetchMock.mock.calls[0][0].pathname).toBe('/api/admin/reviews/r-1/hide');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(fetchMock.mock.calls[1][0].pathname).toBe('/api/admin/reviews/r-1/unhide');
  });
});
