import { describe, it, expect } from 'vitest';
import { allJavaEnums, javaEnumFile, javaEnumValues } from '../test/javaEnum';
import { DELIVERY_CARRIERS } from './order';
import { DISCOUNT_TYPE_LABEL, PROMOTION_KIND_LABEL, PROMOTION_KIND_ORDER } from './coupon';
import { INQUIRY_STATUS_TEXT, INQUIRY_TYPE_TEXT } from './inquiry';
import { GRADE_LABEL, POINT_TYPE_LABEL } from './point';
import { STATUS_OPTIONS, STOCK_REASON_LABEL } from './product';
import { ROLE_LABEL } from './member';

/**
 * 서버 enum ↔ 화면 라벨 맵 드리프트 — **전부** (2026-09-18, BACKLOG O-8).
 *
 * 🔴 **백엔드 원문을 읽어 대조하는 것이 둘뿐이었다**(`order.test.js` 주문 상태 · `audit.test.js` 감사).
 *    나머지 라벨 맵은 enum 에 값이 늘어도 아무것도 안 빨개지고, 화면은 **원문(`RETURN_REQUESTED` 같은 날문자)** 을 띄운다.
 *    ⚠ 2026-09-18 실측으로는 **빠진 값이 0개**였다 — 지금 틀린 게 아니라 «늘 때 아무도 안 묻는» 자리다(WA §2-12).
 *
 * 🔴 **택배사는 손목록끼리 대조하고 있었다**(`order.test.js` 의 `toEqual(['CJ', …])`). 값이 늘면 코드와 테스트가
 *    **같이** 낡는다 — WA §1-2-1 이 «지키는 코드와 다른 모양이어야 한다» 고 막은 모양 그대로다. 여기로 옮겼다.
 */

/**
 * 원문과 대조하는 라벨 맵. `keys` 는 **화면이 실제로 쓰는 객체에서** 꺼낸다(베껴 적지 않는다).
 * 같은 enum 을 여러 맵이 쓰면 줄을 나눈다 — 하나만 늘리고 하나를 잊는 것이 바로 막을 일이다.
 */
const LABELED = [
  { enumName: 'DeliveryCarrier', map: 'order.js DELIVERY_CARRIERS', keys: DELIVERY_CARRIERS.map((c) => c.value) },
  { enumName: 'DiscountType', map: 'coupon.js DISCOUNT_TYPE_LABEL', keys: Object.keys(DISCOUNT_TYPE_LABEL) },
  { enumName: 'Kind', map: 'coupon.js PROMOTION_KIND_LABEL', keys: Object.keys(PROMOTION_KIND_LABEL) },
  { enumName: 'Kind', map: 'coupon.js PROMOTION_KIND_ORDER', keys: Object.keys(PROMOTION_KIND_ORDER) },
  { enumName: 'InquiryStatus', map: 'inquiry.js INQUIRY_STATUS_TEXT', keys: Object.keys(INQUIRY_STATUS_TEXT) },
  { enumName: 'InquiryType', map: 'inquiry.js INQUIRY_TYPE_TEXT', keys: Object.keys(INQUIRY_TYPE_TEXT) },
  { enumName: 'MemberGrade', map: 'point.js GRADE_LABEL', keys: Object.keys(GRADE_LABEL) },
  { enumName: 'PointType', map: 'point.js POINT_TYPE_LABEL', keys: Object.keys(POINT_TYPE_LABEL) },
  { enumName: 'ProductStatus', map: 'product.js STATUS_OPTIONS', keys: STATUS_OPTIONS.map((s) => s.value) },
  { enumName: 'StockChangeReason', map: 'product.js STOCK_REASON_LABEL', keys: Object.keys(STOCK_REASON_LABEL) },
  { enumName: 'Role', map: 'member.js ROLE_LABEL', keys: Object.keys(ROLE_LABEL) },
];

/** 다른 파일이 이미 원문과 대조한다 — 여기서 또 하면 사본이 둘이 된다. */
const COVERED_ELSEWHERE = {
  OrderStatus: 'api/order.test.js',
  AuditAction: 'api/audit.test.js',
  AuditTargetType: 'api/audit.test.js',
};

/**
 * 라벨 맵이 **없어야 맞는** enum — 이유와 함께 (WA §2-12 «결정을 강제하는 목록»).
 * ⚠ «지금 없다» 를 베낀 것이 아니라 «없어야 하는 이유» 를 적는다. 이유가 안 서면 LABELED 로 간다.
 */
const NO_LABEL_BY_DESIGN = {
  ErrorCode: '서버가 message 를 실어 보낸다 — 화면은 code 로 분기만 하고 문구를 다시 쓰지 않는다',
  NotificationType: '서버가 label 을 실어 보낸다(NotificationResponse · NotificationSettingResponse)',
  CouponListStatus: '요청 파라미터다 — 화면이 고르는 값이라 새 값은 «탭을 새로 만드는 일» 이지 라벨만 빠질 수 없다',
  Op: 'QueryDSL 조건 연산자 — 서버 안에서만 쓰인다',
  Transform: 'QueryDSL 조건 값 변환 — 서버 안에서만 쓰인다',
};

describe('서버 enum ↔ 화면 라벨 맵 (2026-09-18, O-8)', () => {
  it.each(LABELED)('🔴 파서가 $enumName 의 값을 실제로 찾았다 — 0개면 아래 대조가 「0 == 0」으로 초록이 된다', ({ enumName }) => {
    expect(javaEnumValues(javaEnumFile(enumName), enumName).length).toBeGreaterThanOrEqual(2);
  });

  it.each(LABELED)('🔴 $map 의 키 집합이 $enumName 과 **정확히** 같다', ({ enumName, keys }) => {
    // 정렬해서 비교한다 — 순서는 계약이 아니다(선택지 순서는 화면이 정한다).
    expect([...keys].sort()).toEqual(javaEnumValues(javaEnumFile(enumName), enumName).sort());
  });

  it('🔴 중첩 enum 도 읽는다 — `PromotionSpanResponse.Kind` 는 8칸 들여쓰기라 예전 두 파서가 못 읽었다', () => {
    // ⚠ `Kind` 는 `;` 없이 `}` 로 끝난다 — «첫 `;` 까지» 로 읽으면 enum 을 지나쳐 다음 메서드까지 읽고
    //    마지막 값 SALE 이 빠진다(2026-09-18 탐색에서 실제로 빠졌다 — `test/javaEnum.js` 머리 주석).
    expect(javaEnumValues(javaEnumFile('Kind'), 'Kind')).toEqual(['ISSUE', 'USE', 'SALE']);
  });
});

describe('덮개 — 백엔드 enum 은 **전부** 셋 중 하나로 정해져 있다 (WA §2-12)', () => {
  const all = allJavaEnums();
  const decided = new Set([
    ...LABELED.map((l) => l.enumName),
    ...Object.keys(COVERED_ELSEWHERE),
    ...Object.keys(NO_LABEL_BY_DESIGN),
  ]);

  it('🔴 다 셌다 — 백엔드에서 enum 을 실제로 찾았다(0개면 아래 덮개가 「빈 목록 ⊆ 뭐든」으로 초록이 된다)', () => {
    // 2026-09-18 실측 18개. 줄면 파서나 경로가 헛돈 것이다 — 늘어나는 것은 아래 덮개가 받는다.
    expect(all.length).toBeGreaterThanOrEqual(18);
  });

  it('🔴 새 enum 이 오면 여기서 빨개진다 — 라벨을 대조할지, 왜 안 하는지 정하고 들어온다', () => {
    const undecided = all.map((e) => `${e.name} (${e.rel})`).filter((s) => !decided.has(s.split(' ')[0]));
    expect(undecided).toEqual([]);
  });

  it('결정 목록에 **없어진 enum** 이 남아 있지 않다 — 죽은 줄은 «지켜지고 있다» 로 읽힌다', () => {
    const names = new Set(all.map((e) => e.name));
    expect([...decided].filter((n) => !names.has(n))).toEqual([]);
  });

  it('한 enum 이 두 칸에 동시에 있지 않다 — «대조함» 과 «안 함» 이 같이 참일 수 없다', () => {
    const labeled = new Set(LABELED.map((l) => l.enumName));
    const elsewhere = Object.keys(COVERED_ELSEWHERE);
    const noLabel = Object.keys(NO_LABEL_BY_DESIGN);
    expect(elsewhere.filter((n) => labeled.has(n) || noLabel.includes(n))).toEqual([]);
    expect(noLabel.filter((n) => labeled.has(n))).toEqual([]);
  });
});
