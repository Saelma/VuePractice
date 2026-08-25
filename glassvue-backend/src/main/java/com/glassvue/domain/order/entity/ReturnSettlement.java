package com.glassvue.domain.order.entity;

/**
 * 반품 한 회차의 정산 결과 (2026-08-25, BACKLOG G-10).
 *
 * <p>🔴 <b>왜 셋을 함께 내나</b> — 셋 다 {@code PointService.refundReturnedOrder} 의 인자이고,
 * 같은 배분에서 <b>동시에</b> 나온다. 따로 계산하게 두면 «환불은 이번 몫인데 적립 회수는 전액» 같은
 * 어긋남이 생긴다 — 2026-08-24 사고가 정확히 그 모양이었다(목록은 맞고 «양»이 틀렸다, WA §1-2-1).
 *
 * @param refundAmount     적립금으로 돌려줄 금액 = 반품금액 − 쿠폰 몫.
 *                         ⚠ <b>사용했던 적립금 몫이 여기 이미 들어 있다</b>(반품 환불의 정의).
 * @param earnedToReverse  회수할 배송완료 적립 — 비례·내림
 * @param purchaseToRemove 등급 누적 구매액에서 뺄 금액 = 반품금액 − 쿠폰 몫 − 적립금 몫
 */
public record ReturnSettlement(long refundAmount, long earnedToReverse, long purchaseToRemove) {
}
