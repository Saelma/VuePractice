import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  orderStatusText, orderStatusClass, ORDER_STATUS_TEXT,
  DELIVERY_CARRIERS, shipOrder, deliverOrder, cancelOrder, resolveOrderStatusFilter,
} from './order';
import { clearSession } from '../stores/auth';

describe('order status 헬퍼', () => {
  it('알려진 상태를 한글로 매핑', () => {
    expect(orderStatusText('ORDERED')).toBe('결제대기');
    expect(orderStatusText('PAID')).toBe('결제완료');
    expect(orderStatusText('SHIPPED')).toBe('발송완료');
    expect(orderStatusText('DELIVERED')).toBe('배송완료');
    expect(orderStatusText('CANCELLED')).toBe('취소됨');
    expect(orderStatusText('RETURN_REQUESTED')).toBe('반품요청');
    expect(orderStatusText('RETURNED')).toBe('반품완료');
    expect(Object.keys(ORDER_STATUS_TEXT)).toHaveLength(7);
  });

  it('모르는 상태는 원문 그대로', () => {
    expect(orderStatusText('WEIRD')).toBe('WEIRD');
  });

  // 색을 직접 쓰지 않고 공용 badge 변형을 돌려준다(DESIGN.md §5) — 화면마다 매핑이 갈리지 않게.
  it('상태별 배지 변형이 다르고, 모르는 값은 neutral 기본', () => {
    expect(orderStatusClass('ORDERED')).toBe('badge-warning');
    expect(orderStatusClass('PAID')).toBe('badge-success');
    // 발송완료는 이제 종착이 아니다(배송완료가 남았다) → 중립. 배송완료가 정상 종료.
    expect(orderStatusClass('SHIPPED')).toBe('badge-neutral');
    expect(orderStatusClass('DELIVERED')).toBe('badge-success');
    expect(orderStatusClass('CANCELLED')).toBe('badge-danger');
    expect(orderStatusClass('XXX')).toBe('badge-neutral');
  });
});

describe('택배사 선택지', () => {
  // 값은 백엔드 DeliveryCarrier enum 이름과 같아야 한다 — 다르면 서버가 400으로 거른다.
  it('백엔드 enum 이름과 같은 값을 쓴다', () => {
    expect(DELIVERY_CARRIERS.map((c) => c.value))
      .toEqual(['CJ', 'KOREA_POST', 'HANJIN', 'LOTTE', 'LOGEN', 'ETC']);
  });

  it('모든 선택지에 표시명이 있다', () => {
    for (const c of DELIVERY_CARRIERS) {
      expect(c.text).toBeTruthy();
    }
  });
});

// 발송 처리는 운송장이 필수로 바뀌었다(V13). 본문 없이 보내면 서버가 400을 내므로,
// 요청이 실제로 택배사·송장번호를 담는지 계약으로 고정한다.
describe('발송·배송완료 요청', () => {
  const okRes = { ok: true, status: 200, json: () => Promise.resolve({ success: true, data: null }) };

  beforeEach(() => {
    clearSession();
    vi.restoreAllMocks();
  });

  it('shipOrder는 택배사·송장번호를 본문에 담아 POST한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okRes);
    global.fetch = fetchMock;

    await shipOrder('order-1', { carrier: 'CJ', trackingNo: '123456789012' });

    // client.js는 fetch(url, options)에 **URL 객체**를 넘긴다(문자열이 아니다).
    const [url, init] = fetchMock.mock.calls[0];
    expect(url.pathname).toBe('/api/orders/order-1/ship');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ carrier: 'CJ', trackingNo: '123456789012' });
  });

  it('deliverOrder는 본문 없이 deliver 경로로 POST한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okRes);
    global.fetch = fetchMock;

    await deliverOrder('order-1');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url.pathname).toBe('/api/orders/order-1/deliver');
    expect(init.method).toBe('POST');
  });

  // 취소 사유(2026-08-04, B-17). **선택**이라 "안 보낸 것" 과 "빈 문자열" 이 같은 뜻이어야 한다 —
  // 빈 문자열을 그대로 보내면 서버가 공백을 받고, 화면은 나중에 "사유가 있다" 로 읽어 빈 칸을 그린다.
  it('cancelOrder는 사유를 본문에 담아 POST한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okRes);
    global.fetch = fetchMock;

    await cancelOrder('order-1', '배송이 늦어서');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url.pathname).toBe('/api/orders/order-1/cancel');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ reason: '배송이 늦어서' });
  });

  it('⚠ cancelOrder는 사유가 없거나 빈 문자열이면 **null** 로 보낸다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okRes);
    global.fetch = fetchMock;

    await cancelOrder('order-1');
    await cancelOrder('order-2', '');

    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ reason: null });
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ reason: null });
  });
});

describe('resolveOrderStatusFilter — 관리자 홈에서 "할 일"을 집어 올 때 (B-16)', () => {
  it('아는 상태는 그대로 쓴다', () => {
    expect(resolveOrderStatusFilter('PAID')).toBe('PAID');
    expect(resolveOrderStatusFilter('RETURN_REQUESTED')).toBe('RETURN_REQUESTED');
  });

  it('값이 없으면 발송 대기(PAID) — 이 화면에 오는 가장 흔한 이유', () => {
    expect(resolveOrderStatusFilter(undefined)).toBe('PAID');
    expect(resolveOrderStatusFilter(null)).toBe('PAID');
    expect(resolveOrderStatusFilter('')).toBe('PAID');
  });

  it('⚠ 모르는 값은 통과시키지 않는다 — 400이 "주문이 없다"로 보이는 걸 막는다', () => {
    expect(resolveOrderStatusFilter('PAIDD')).toBe('PAID');
    expect(resolveOrderStatusFilter('paid')).toBe('PAID'); // 서버 enum은 대문자
    expect(resolveOrderStatusFilter(['PAID', 'SHIPPED'])).toBe('PAID'); // ?status=a&status=b
  });

  it('⚠ Object 프로토타입 속성에 속지 않는다 (?status=toString)', () => {
    expect(resolveOrderStatusFilter('toString')).toBe('PAID');
    expect(resolveOrderStatusFilter('constructor')).toBe('PAID');
  });
});
