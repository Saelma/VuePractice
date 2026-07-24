import { apiGet } from './client';

/**
 * 관리자 매출 통계 (2026-07-24, 백로그 C-11).
 *
 * 요약·일별 추이·상품별 TOP을 **한 응답**으로 받는다. 대시보드 하나라 세 번 왕복할 이유가 없고,
 * 세 값이 같은 시점을 보고 있다는 것도 보장된다(따로 부르면 그 사이에 주문이 들어와 어긋난다).
 */
export function fetchSalesOverview() {
  return apiGet('/api/admin/stats/sales');
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
