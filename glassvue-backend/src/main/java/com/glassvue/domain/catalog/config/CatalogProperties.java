package com.glassvue.domain.catalog.config;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.validation.annotation.Validated;
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
@Validated
@ConfigurationProperties(prefix = "catalog")
public record CatalogProperties(
        /** ⚠ <b>0 이 유효하다</b> — 「품절도 포함」이라는 뜻이다(위 설명). 그래서 {@code @PositiveOrZero} 다. */
        @PositiveOrZero long lowStockThreshold,
        /**
         * 🔴 <b>0 이면 「삭제 대기」가 즉시 영구 삭제된다</b> — F-7 이 만든 안전망이 통째로 사라진다.
         * ⚠ 되돌릴 수 없는 구간이라 이 값의 오타가 가장 비싸다.
         */
        @Positive int purgeGraceDays,
        boolean purgeEnabled) {
}
