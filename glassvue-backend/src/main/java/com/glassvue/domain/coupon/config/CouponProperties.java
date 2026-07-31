package com.glassvue.domain.coupon.config;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * coupon.* 설정 (2026-07-31, G-2).
 *
 * <p>⚠ <b>타입이 {@code UUID} 가 아니라 {@code String} 인 이유</b>: 값을 안 넣으면
 * ({@code ${WELCOME_COUPON_ID:}} → 빈 문자열) UUID 바인딩이 <b>기동 시점에</b> 깨진다.
 * "가입 쿠폰을 안 쓴다" 는 정상 상태이지 설정 오류가 아니므로, 여기서는 문자열로 받고
 * {@link #welcomeCouponId()} 가 <b>못 읽으면 비어 있는 값</b>으로 답한다.
 *
 * <p>잘못된 값이어도 기동은 시킨다 — 화면이 가입 쿠폰을 광고할지는 <b>DB 에서 실제로 조회된
 * 쿠폰이 있을 때만</b> 정해지므로({@code GET /api/coupons/welcome}), 설정이 틀리면 광고도 안 나간다.
 * 즉 <b>거짓말이 생기지 않아</b> 기동을 막을 이유가 없다(E-3 의 흔한비밀번호 목록과는 반대 판단 —
 * 그건 못 읽으면 <b>조용히 약해지므로</b> 기동을 막았다).
 *
 * @param welcomeCouponId 가입 즉시 자동 발급할 쿠폰 정의 id. 비우면 기능이 꺼진다.
 */
@ConfigurationProperties(prefix = "coupon")
public record CouponProperties(String welcomeCouponId) {

    /** 설정이 비었거나 UUID 형식이 아니면 {@link Optional#empty()}. */
    public Optional<UUID> welcomeCoupon() {
        if (welcomeCouponId == null || welcomeCouponId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(welcomeCouponId.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
