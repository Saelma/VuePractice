package com.glassvue.global.messaging;

/**
 * 도메인 이벤트 공통 계약(마커). 모든 도메인 이벤트는 이 인터페이스를 implements 한다.
 * 지금은 스프링 ApplicationEventPublisher로 발행되지만, MSA 단계에선 이 계약이
 * 메시지(RabbitMQ) 페이로드로 승격된다 — 그래서 구현체가 아닌 global/messaging 에 둔다.
 * (지금은 컨벤션·미래 대비용 마커. 범용 장치(아웃박스 등)가 생기면 실질 활용된다.)
 */
public interface DomainEvent {
}
