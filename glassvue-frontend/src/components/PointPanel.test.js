import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 적립금·등급 패널 (2026-08-28, BACKLOG G-6).
//
// 🔴 **이 파일이 생긴 이유는 등급 혜택이 둘이 됐기 때문이다.** 그전에는 등급의 효과가 적립률
//    하나였고, 그 한 줄은 서버 DTO 를 그대로 찍기만 해 테스트할 게 없었다. 무료배송 기준이
//    붙으면서 **화면이 「보일 때와 안 보일 때」를 가르게 됐다**(무료배송 정책이 없으면 0).
//
// ⚠ 여기서 지키는 것은 «금액이 맞나» 가 아니다 — 그건 서버(MemberGradeTest·PointServiceTest)가
//    본다. 여기서는 **화면이 계산을 다시 하지 않는다**는 것을 지킨다: 인하율도 기본 기준도
//    화면에 없고, 서버가 준 freeShippingThreshold 를 그대로 쓴다.

const fetchPointAccount = vi.fn();
const fetchPointHistory = vi.fn();
vi.mock('../api/point', () => ({
  fetchPointAccount: (...a) => fetchPointAccount(...a),
  fetchPointHistory: (...a) => fetchPointHistory(...a),
  gradeText: (g) => ({ BRONZE: '브론즈', SILVER: '실버', GOLD: '골드', VIP: 'VIP' })[g] || g,
  pointTypeText: (t) => t,
}));
vi.mock('../api/product', () => ({ priceText: (n) => `${Number(n).toLocaleString()}원` }));

import PointPanel from './PointPanel.vue';

/** 서버 응답(`GET /api/points/me`) 모양. freeShippingThreshold 가 G-6 에서 더해졌다. */
function account(overrides = {}) {
  return {
    balance: 1200,
    totalPurchase: 150_000,
    grade: 'SILVER',
    earnPercent: 2,
    nextGrade: 'GOLD',
    amountToNextGrade: 350_000,
    freeShippingThreshold: 24_000,
    ...overrides,
  };
}

async function mountPanel(acc) {
  fetchPointAccount.mockResolvedValue(acc);
  fetchPointHistory.mockResolvedValue({ content: [], totalElements: 0 });
  const w = mount(PointPanel);
  await flushPromises();
  return w;
}

describe('PointPanel — 등급 혜택', () => {
  beforeEach(() => vi.clearAllMocks());

  it('등급 혜택을 **둘 다** 보여준다 — 적립률과 무료배송 기준', async () => {
    const w = await mountPanel(account());
    expect(w.text()).toContain('2% 적립');
    expect(w.text()).toContain('24,000원 이상 무료배송');
  });

  it('🔴 서버가 준 기준을 그대로 쓴다 — 화면이 인하율을 다시 계산하지 않는다', async () => {
    // VIP 라고 화면이 「60% 인하」를 아는 게 아니다. 서버가 12,000 을 주면 12,000 을 찍는다.
    const w = await mountPanel(account({ grade: 'VIP', earnPercent: 5, freeShippingThreshold: 12_000 }));
    expect(w.text()).toContain('12,000원 이상 무료배송');
    expect(w.text()).not.toContain('24,000');
  });

  it('무료배송 정책이 없으면(0) 그 줄을 안 띄운다 — "0원 이상 무료배송"이 되면 안 된다', async () => {
    const w = await mountPanel(account({ freeShippingThreshold: 0 }));
    expect(w.text()).not.toContain('무료배송');
    expect(w.text()).toContain('2% 적립');   // 적립률 줄은 그대로 있다
  });
});
