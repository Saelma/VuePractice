package com.glassvue.domain.notification;

import com.glassvue.domain.catalog.event.StockRunningLowEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 재고 부족 알림 처리 — 이벤트에 반응하는 "진짜 주체". 전송수단(스프링 이벤트/RabbitMQ)과 무관한 순수 로직.
 * 지금은 로그 stub(관리자 메일/발주 연동은 이후 단계). 배치나 다른 진입점에서 직접 호출해도 된다.
 */
@Slf4j
@Component
public class StockAlertHandler {

    public void handle(StockRunningLowEvent event) {
        if (event.remainingStock() == 0) {
            log.warn("[재고] 품절 — product={}({}) threshold={}",
                    event.productName(), event.productId(), event.threshold());
            return;
        }
        log.warn("[재고] 재고 부족(stub) — product={}({}) remaining={} threshold={}",
                event.productName(), event.productId(), event.remainingStock(), event.threshold());
    }
}
