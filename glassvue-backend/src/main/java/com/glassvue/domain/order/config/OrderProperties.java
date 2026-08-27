package com.glassvue.domain.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * order.* 설정.
 *
 * @param returnGraceDays 반품 요청 가능 기간(일). <b>배송완료 시각부터</b> 이만큼 안에만 요청할 수 있다
 *                        (2026-08-27, BACKLOG §I-9 결정 1).
 *                        <p>🔴 <b>기본값 7 은 우리가 고른 숫자가 아니다</b> — 전자상거래법 §17 의
 *                        단순변심 청약철회 기간과 같은 줄을 쓴다. «왜 7일인가» 의 근거가
 *                        <b>우리 밖에 있다는 것</b>이 이 값의 값이다.
 *                        <p>⚠ <b>이 값을 줄이면 이미 배송된 주문이 앞당겨 막힌다</b> — 경과 시간으로
 *                        판단하므로({@code catalog.purge-grace-days} 와 같은 성질).
 *                        <p>⚠ <b>0 이하로 두지 말 것</b> — 배송완료 즉시 반품이 막힌다.
 *                        기한을 없애려면 이 값을 크게 두지, 0 으로 두지 않는다.
 */
@ConfigurationProperties(prefix = "order")
public record OrderProperties(int returnGraceDays) {
}
