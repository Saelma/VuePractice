import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  fetchAdminInquiries, answerInquiry, INQUIRY_STATUS_OPTIONS,
  hideInquiry, unhideInquiry, INQUIRY_HIDDEN_OPTIONS,
  createGeneralInquiry, fetchMyInquiries, inquiryTypeText, GENERAL_INQUIRY_TYPE_OPTIONS,
} from './inquiry';
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

  // ── 숨김 필터 (2026-08-10, B-18 잔여) ──────────────────────────────────

  it('🔴 hidden=false 는 **보내진다** — falsy 라고 빠지면 「보이는 것만」이 「전체」가 된다', async () => {
    // status 와 달리 여기는 false 가 의미 있는 값이다. apiGet 은 null 만 빼야 한다.
    expect((await callOf({ hidden: false })).searchParams.get('hidden')).toBe('false');
    expect((await callOf({ hidden: true })).searchParams.get('hidden')).toBe('true');
  });

  it('hidden 이 null 이면(=전체) 파라미터가 아예 빠진다 — status 와 같은 세 가지 상태', async () => {
    expect((await callOf({ hidden: null })).searchParams.has('hidden')).toBe(false);
    expect((await callOf()).searchParams.has('hidden')).toBe(false);
  });

  it('선택지가 세 가지 상태를 다 갖는다 — 「전체」는 null, 나머지는 boolean', () => {
    expect(INQUIRY_HIDDEN_OPTIONS.map((o) => o.value)).toEqual([false, true, null]);
    // ⚠ 「전체」를 문자열 'ALL' 로 두면 서버가 Boolean 변환에 실패해 400 이고,
    //    화면엔 그게 "문의가 없다" 로 보인다(status 와 같은 함정).
    expect(INQUIRY_HIDDEN_OPTIONS.find((o) => o.text === '전체').value).toBeNull();
  });

  it('status 와 hidden 을 **함께** 보낸다 — 둘은 독립이라 한쪽이 다른 쪽을 지우면 안 된다', async () => {
    const url = await callOf({ status: 'WAITING', hidden: false });
    expect(url.searchParams.get('status')).toBe('WAITING');
    expect(url.searchParams.get('hidden')).toBe('false');
  });
});

describe('문의 숨김 · 해제 (B-18 잔여)', () => {
  const okRes = { ok: true, status: 200, json: () => Promise.resolve({ success: true, data: null }) };

  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  it('관리자 경로로 POST 한다 — 숨김과 해제가 **다른 엔드포인트**다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okRes);
    global.fetch = fetchMock;

    await hideInquiry('i-1');
    await unhideInquiry('i-1');

    expect(fetchMock.mock.calls[0][0].pathname).toBe('/api/admin/inquiries/i-1/hide');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(fetchMock.mock.calls[1][0].pathname).toBe('/api/admin/inquiries/i-1/unhide');
    // ⚠ 하나의 토글 엔드포인트로 합치지 않았다 — 서버가 「이미 그 상태면 아무것도 안 한다」로
    //    멱등을 보장하는데, 토글이면 두 번 눌렀을 때 원래대로 돌아가 그 보장이 사라진다.
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

/**
 * 일반 고객센터 문의 · 내 문의 (2026-08-07, G-3 2·3단계).
 *
 * ⚠ 여기서 지키는 것은 **«경로가 유형을 정한다»** 는 갈래다. 작성 경로가 둘인데 둘이 섞이면
 * 상품 문의가 상품 없이 저장되거나(관리자 목록의 상품명이 영원히 빈다), 일반 문의가 상품에 붙는다
 * (상품 문의 목록에 섞여 뜬다). 둘 다 **앱은 멀쩡히 돌면서** 틀린다.
 */
describe('일반 고객센터 문의 (G-3 2단계)', () => {
  const okRes = { ok: true, status: 200, json: () => Promise.resolve({ success: true, data: 'new-id' }) };

  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  async function postOf(payload) {
    const fetchMock = vi.fn().mockResolvedValue(okRes);
    global.fetch = fetchMock;
    await createGeneralInquiry(payload);
    return fetchMock.mock.calls[0];
  }

  it('🔴 **상품 경로가 아니라** `/api/inquiries` 로 POST 한다(상품을 고르지 않는 것이 이 기능이다)', async () => {
    const [url, init] = await postOf({ type: 'DELIVERY', title: 'ZZ', content: 'c', secret: true });

    expect(url.pathname).toBe('/api/inquiries');
    expect(init.method).toBe('POST');
  });

  it('유형을 본문에 실어 보낸다 — 일반 문의는 **사용자가 고른다**', async () => {
    const [, init] = await postOf({ type: 'REFUND', title: 'ZZ', content: 'c', secret: true });

    expect(JSON.parse(init.body).type).toBe('REFUND');
  });

  it('🔴 선택지에 PRODUCT 가 **없다** — 상품 문의는 상품 경로가 정하는 값이라 고를 수 없어야 한다', () => {
    // 보내면 서버가 400(INQUIRY-400T)이지만, 애초에 고를 수 없어야 사용자가 막다른 길을 안 만난다.
    expect(GENERAL_INQUIRY_TYPE_OPTIONS.map((o) => o.value)).not.toContain('PRODUCT');
    expect(GENERAL_INQUIRY_TYPE_OPTIONS.map((o) => o.value)).toEqual(['DELIVERY', 'REFUND', 'ETC']);
  });

  it('⚠ 표시 문구는 **선택지보다 넓다** — 목록엔 PRODUCT 도, 나중엔 PAYMENT·ACCOUNT 도 섞여 온다', () => {
    // V42 가 값을 넉넉히 열어 뒀다(Oracle 은 나중에 enum 을 늘리면 CHECK 를 못 고쳐 수동 ALTER 다).
    // 문구가 없으면 그 줄이 «PAYMENT» 라는 날문자로 보인다.
    ['PRODUCT', 'DELIVERY', 'REFUND', 'PAYMENT', 'ACCOUNT', 'ETC'].forEach((t) => {
      expect(inquiryTypeText(t)).not.toBe(t);
    });
  });

  it('모르는 유형은 **날문자 그대로** 돌려준다(빈칸으로 삼키지 않는다)', () => {
    expect(inquiryTypeText('SOMETHING_NEW')).toBe('SOMETHING_NEW');
  });
});

describe('내 문의 목록 (G-3 3단계)', () => {
  const okRes = { ok: true, status: 200, json: () => Promise.resolve({ success: true, data: null }) };

  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  it('🔴 **작성자를 파라미터로 보내지 않는다** — 서버가 로그인에서 뽑는다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okRes);
    global.fetch = fetchMock;

    await fetchMyInquiries({ page: 1, size: 10 });
    const url = fetchMock.mock.calls[0][0];

    // authorId 를 실어 보내는 순간 남의 문의를 읽는 길이 열린다 — 목록 API 의 가장 흔한 구멍이다.
    expect(url.pathname).toBe('/api/inquiries/me');
    expect(url.searchParams.has('authorId')).toBe(false);
    expect(url.searchParams.has('memberId')).toBe(false);
    expect(url.searchParams.get('page')).toBe('1');
  });
});
