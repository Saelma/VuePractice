import { apiGet } from './client';

/**
 * 관리자 **기간 선택**의 공용 계산 (B-26 · DESIGN §7).
 *
 * ⚠ **매출 전용이 아니다.** 2026-08-13 에 매출 화면에서 태어나 `api/stats.js` 에 있었는데,
 * 2026-09-07 에 주문·감사 목록이 같은 컨트롤을 쓰게 되면서 여기로 옮겼다.
 * 🔴 **화면마다 베끼면 「이번 달」이 화면마다 다른 기간을 뜻하게 된다** — `ProductCard` 를
 * 공용으로 되돌린 것과 같은 판단이다.
 *
 * 🔴 **`today` 는 인자로 받는다 — 여기서 `new Date()` 를 부르지 않는다.**
 * 브라우저 시계가 기준이 되면 장부(KST)와 어긋나고, 그 어긋남은 화면에 안 보인다.
 * 기준점은 서버가 준다(`GET /api/policy/today`).
 */
/**
 * 프리셋 기간 (B-26, 2026-08-13).
 *
 * 🔴 **기준 날짜(`today`)를 인자로 받는다 — 브라우저 시계로 「오늘」을 정하지 않는다.**
 * 매출 장부는 KST 기준인데 `new Date()` 는 보는 사람의 시간대를 따른다. 서버가 응답에
 * 돌려준 `to`(파라미터 없이 부르면 KST 오늘)를 기준으로 삼으면 **기준이 한 곳**에 남는다.
 *
 * ⚠ 날짜 산수는 **UTC 로 한다**. `new Date('2026-08-13')` 에 로컬 시간대가 끼면 하루가 밀 수 있어서,
 * 여기서는 연·월·일을 뜯어 `Date.UTC` 로 다루고 다시 문자열로 조립한다 — 시간대가 개입할 틈이 없다.
 *
 * ⚠ 서버도 같은 것을 계산하지 않는다. 서버는 **받은 날짜의 경계**만 만들고,
 * 「지난 달이 며칟날부터인가」는 화면이 정한다 — 그건 경계가 아니라 **달력 산수**라 안 흔들린다.
 */
export const PRESETS = [
  { key: 'today', label: '오늘' },
  { key: '7d', label: '7일' },
  { key: '30d', label: '30일' },
  { key: 'thisMonth', label: '이번 달' },
  { key: 'lastMonth', label: '지난 달' },
];

export function presetRange(key, today) {
  const [y, m, d] = today.split('-').map(Number);
  const at = (yy, mm, dd) => fmt(new Date(Date.UTC(yy, mm - 1, dd)));
  const minusDays = (n) => fmt(new Date(Date.UTC(y, m - 1, d - n)));

  switch (key) {
    case 'today':
      return { from: today, to: today };
    case '7d':
      return { from: minusDays(6), to: today };
    case '30d':
      return { from: minusDays(29), to: today };
    case 'thisMonth':
      return { from: at(y, m, 1), to: today };
    case 'lastMonth': {
      const first = new Date(Date.UTC(y, m - 2, 1));
      // 「지난 달 마지막 날」 = 이번 달 1일에서 하루 뺀 날. 말일을 직접 세지 않는다(28·29·30·31).
      const last = new Date(Date.UTC(y, m - 1, 0));
      return { from: fmt(first), to: fmt(last) };
    }
    default:
      return { from: today, to: today };
  }
}

/** 지금 고른 구간이 어느 프리셋과 같은지 — 버튼을 활성으로 그리기 위해서다. 없으면 null. */
export function matchedPreset(from, to, today) {
  if (!from || !to || !today) return null;
  const hit = PRESETS.find((p) => {
    const r = presetRange(p.key, today);
    return r.from === from && r.to === to;
  });
  return hit ? hit.key : null;
}

function fmt(date) {
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, '0');
  const d = String(date.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/**
 * 서버가 보는 «오늘»(KST). 프리셋의 **유일한 기준점**이다.
 *
 * 🔴 **`new Date()` 로 대신하지 않는다.** 시간대를 옳게 변환해도 **브라우저 시계 자체가 틀릴 수 있고**,
 * 그러면 같은 「이번 달」이 사람마다 다른 기간을 뜻한다. 장부는 KST 한 벌이다(B-26).
 *
 * ⚠ 매출 화면은 이걸 안 쓰고 **응답의 `to`** 에서 기준을 얻는다(파라미터 없이 부르면 그게 KST 오늘이다).
 * 목록 화면은 그런 응답이 없어서 이 엔드포인트가 생겼다.
 */
export function fetchServerToday() {
  return apiGet('/api/policy/today');
}
