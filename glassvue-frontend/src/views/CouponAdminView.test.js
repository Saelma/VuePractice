import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

// 🔴 **이 저장소의 첫 뷰 테스트다** (2026-08-14).
//
// 그동안 `components/` 에는 테스트가 있었지만 `views/` 에는 하나도 없었고, 그 대가가 2026-08-13 에
// 실제로 나왔다: 이벤트 쿠폰을 만들려던 두 건이 **상시 쿠폰으로 조용히 만들어졌는데**
// **서버 테스트는 전부 초록**이었다(배지·겹침검사·배너가 전부 「설계대로」 안 나온 것이라
// 서버에는 틀린 곳이 없다). 그래서 폼이 «자기가 만들 것» 을 문장으로 되읽게 고쳤고,
// **그 문장이 이 화면의 유일한 방어**다 — 여기가 첫 뷰 테스트의 자리인 이유다.
//
// ⚠ **이 뷰를 고른 또 하나의 이유는 mount 비용**이다. DevExtreme 도 router 도 안 쓴다
//    (api 모듈 둘 + EmptyState 뿐) — **프로덕션 코드를 테스트용으로 뜯지 않아도 된다.**
//    router·DevExtreme 을 쓰는 화면은 그 자체가 별도 판단이라 여기서 끌고 오지 않는다.
//
// ⚠ **computed 를 직접 부르지 않는다.** 날짜 칸을 실제로 채워 화면에 뜬 문장을 읽는다 —
//    바인딩이 끊어지면(`v-model` 오타 등) 로직만 보는 테스트는 그대로 초록이다.

vi.mock('../api/coupon', () => ({
  fetchAdminCoupons: vi.fn(async () => ({ content: [] })),
  createCoupon: vi.fn(),
  issueCoupon: vi.fn(),
  setWelcomeCoupon: vi.fn(),
  couponDiscountText: (c) => `${c.discountValue}원`,
}));
vi.mock('../api/member', () => ({
  fetchAdminMembers: vi.fn(async () => ({ content: [] })),
  roleText: (r) => r,
}));
vi.mock('../api/product', () => ({ priceText: (n) => `${n}원` }));

import CouponAdminView from './CouponAdminView.vue';

/** 폼의 날짜 세 칸을 실제로 채운다(빈 문자열 = 안 채운 것). */
async function fillDates(w, { validFrom = '', validUntil = '', issueUntil = '' }) {
  const dates = w.findAll('input[type="date"]');
  // 화면 순서: 유효 시작일 · 유효 종료일 · 발급 마감일(이벤트)
  await dates[0].setValue(validFrom);
  await dates[1].setValue(validUntil);
  await dates[2].setValue(issueUntil);
  return w;
}

describe('CouponAdminView — 「지금 무엇을 만들고 있는지」 되읽기', () => {
  let wrapper;

  beforeEach(async () => {
    wrapper = mount(CouponAdminView);
    await flushPromises(); // onMounted 의 목록 로딩
  });

  it('🔴 발급 마감일이 비면 **상시 쿠폰**이라고 말한다 — 08-13 사고가 난 바로 그 상태다', async () => {
    await fillDates(wrapper, { validFrom: '2026-09-01', validUntil: '2026-09-30' });

    expect(wrapper.text()).toContain('상시 쿠폰');
    // 🔴 «홈 배너에 안 뜬다» 까지 말해야 한다 — 사고의 본질은 «이벤트인 줄 알았다» 였다.
    expect(wrapper.text()).toContain('홈 배너에는 안 뜹니다');
    expect(wrapper.text()).not.toContain('이벤트 쿠폰 —');
  });

  it('🔴 발급 마감일을 넣으면 **이벤트 쿠폰**으로 바뀐다 (칸 하나가 종류를 가른다)', async () => {
    await fillDates(wrapper, {
      validFrom: '2026-09-01', validUntil: '2026-10-01', issueUntil: '2026-09-02',
    });

    const text = wrapper.text();
    expect(text).toContain('이벤트 쿠폰 —');
    // 발급 창과 사용 기간이 **다른 것**임이 문장에 드러나야 한다(G-8 이 처음 밟은 혼동).
    expect(text).toContain('2026-09-01 ~ 2026-09-02'); // 받는 기간
    expect(text).toContain('2026-10-01까지 씁니다');    // 쓰는 기한
    expect(text).toContain('「받기」');
  });

  it('날짜가 덜 채워졌으면 **문장을 지어내지 않는다** — 마저 넣으라고 한다', async () => {
    await fillDates(wrapper, { issueUntil: '2026-09-02' }); // 유효 기간이 비었다

    expect(wrapper.text()).toContain('마저 넣어 주세요');
    // ⚠ 여기서 «~ 동안 발급되고» 를 그리면 **빈 값이 섞인 거짓 문장**이 된다.
    expect(wrapper.text()).not.toContain('까지 씁니다');
  });

  it('🔴 발급 마감 == 사용 종료면 **경고한다** — G-8 이 처음 밟은 「받자마자 못 쓴다」', async () => {
    await fillDates(wrapper, {
      validFrom: '2026-09-01', validUntil: '2026-09-02', issueUntil: '2026-09-02',
    });

    expect(wrapper.text()).toContain('그 날 자정까지만');
  });

  it('⚠ 경고는 **막지 않는다** — 「그 날 하루만 쓰는 쿠폰」이 의도일 수 있다', async () => {
    await fillDates(wrapper, {
      validFrom: '2026-09-01', validUntil: '2026-09-02', issueUntil: '2026-09-02',
    });

    // 버튼이 잠기면 의도한 쿠폰을 못 만든다. 말은 하되 길은 열어 둔다.
    const submit = wrapper.find('button[type="submit"]');
    expect(submit.attributes('disabled')).toBeUndefined();
    expect(submit.text()).toContain('이벤트 쿠폰 생성'); // 버튼도 무엇을 만드는지 말한다
  });
});
