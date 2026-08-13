import { apiGet } from './client';

/**
 * 관리자 매출 통계 (2026-07-24, 백로그 C-11).
 *
 * 요약·일별 추이·상품별 TOP을 **한 응답**으로 받는다. 대시보드 하나라 세 번 왕복할 이유가 없고,
 * 세 값이 같은 시점을 보고 있다는 것도 보장된다(따로 부르면 그 사이에 주문이 들어와 어긋난다).
 */
export function fetchSalesOverview({ from, to } = {}) {
  return apiGet('/api/admin/stats/sales', { from, to });
}

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
 * 막대 차트용 높이 비율(%). 최대값 대비로 계산한다.
 *
 * 전부 0이면 0을 돌려준다 — 0으로 나누지 않기 위해서다. 값이 있는 날은 **최소 2%** 를 주는데,
 * 안 그러면 매출이 아주 적은 날이 높이 0이 되어 "매출이 없는 날"과 구분되지 않는다.
 */
export function barHeight(value, max) {
  if (!max || max <= 0) return 0;
  if (!value) return 0;
  return Math.max(2, Math.round((value / max) * 100));
}

/** 'yyyy-MM-dd' → 'M/D'. 축이 30칸이라 연도·앞자리 0은 자리만 차지한다. */
export function shortDate(date) {
  const parts = (date || '').split('-');
  if (parts.length !== 3) return date || '';
  return `${Number(parts[1])}/${Number(parts[2])}`;
}
