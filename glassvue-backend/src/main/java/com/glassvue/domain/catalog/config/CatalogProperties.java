package com.glassvue.domain.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * catalog.* 설정.
 *
 * @param lowStockThreshold 이 값 **이하**로 재고가 떨어지면 StockRunningLowEvent 발행(0=품절도 포함).
 */
@ConfigurationProperties(prefix = "catalog")
public record CatalogProperties(long lowStockThreshold) {
}
