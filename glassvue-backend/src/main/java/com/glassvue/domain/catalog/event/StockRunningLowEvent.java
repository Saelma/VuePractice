package com.glassvue.domain.catalog.event;

import com.glassvue.global.messaging.DomainEvent;
import java.util.UUID;

/**
 * 상품 재고가 임계치 이하로 떨어졌을 때 발행되는 도메인 이벤트.
 * 재고는 catalog가 소유하므로 **발행 주체도 catalog**다 — 주문 말고 다른 경로(관리자 수정 등)로 줄어도
 * 같은 이벤트가 나가야 하기 때문. 덕분에 order 도메인은 이 알림의 존재를 전혀 모른다.
 * 구독자(알림·발주 등)는 notification 도메인에 있고, MSA 단계에선 이 이벤트가 메시지(RabbitMQ)로 승격된다.
 */
public record StockRunningLowEvent(UUID productId, String productName, long remainingStock, long threshold)
        implements DomainEvent {
}
