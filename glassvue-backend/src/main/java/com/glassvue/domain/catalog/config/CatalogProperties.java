package com.glassvue.domain.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * catalog.* 설정.
 *
 * @param lowStockThreshold 이 값 **이하**로 재고가 떨어지면 StockRunningLowEvent 발행(0=품절도 포함).
 * @param purgeGraceDays    상품 삭제 유예 일수 (2026-08-12, F-7). 이만큼 지난 「삭제 대기」 상품을
 *                          배치가 <b>진짜로</b> 지운다. 그 전까지는 관리자가 복구할 수 있다.
 * @param purgeEnabled      배치 on/off. ⚠ <b>끄면 유예가 무한이 된다</b>(아무것도 안 지워진다) —
 *                          «지워지지 않는 것» 이 «잘못 지워지는 것» 보다 나으므로 이쪽이 안전한 기본값이다.
 *                          {@code image.cleanup-enabled} 와 같은 자리.
 */
@ConfigurationProperties(prefix = "catalog")
public record CatalogProperties(long lowStockThreshold, int purgeGraceDays, boolean purgeEnabled) {
}
