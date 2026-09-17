package com.glassvue.domain.coupon.dto;

/**
 * 관리자 쿠폰 목록의 탭 (2026-09-17) — 「사용 가능」·「만료됨」.
 *
 * <p>⚠ 경계는 {@code Coupon.isExpiredAt} 과 같다 — <b>사용 마감이 지났으면</b> 만료다. 시작 전 쿠폰은
 * 「사용 가능」 쪽이다(받아 두면 나중에 쓰인다). 두 자리가 갈리면 목록의 탭과 줄의 «만료» 표시가 어긋난다.
 */
public enum CouponListStatus {
    ACTIVE,
    EXPIRED
}
