package com.glassvue.domain.catalog.service.command;

import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.order.event.SoldLine;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매량 집계를 상품에 반영하는 진짜 주체(Handler). {@link RatingSyncHandler} 와 같은 규약 —
 * 리스너는 위임만 하고 로직은 여기 있다. MSA 전환 시 리스너 자리에 RabbitMQ 컨슈머만 갈아끼운다.
 *
 * <p>리스너가 AFTER_COMMIT + @Async 라 원 트랜잭션은 이미 끝났다 → 자체 트랜잭션이 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesSyncHandler {

    private final ProductRepository productRepository;

    /** 주문됨 — 판매량을 늘린다. */
    @Transactional
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void increase(List<SoldLine> lines) {
        apply(lines, +1);
    }

    /** 취소·반품 — 판매량을 되돌린다(주문의 반대). */
    @Transactional
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void decrease(List<SoldLine> lines) {
        apply(lines, -1);
    }

    private void apply(List<SoldLine> lines, int sign) {
        for (SoldLine line : lines) {
            long delta = sign * line.quantity();
            int updated = productRepository.addSoldCount(line.productId(), delta);
            if (updated == 0) {
                // 이벤트와 처리 사이에 상품이 삭제된 경우 — 갱신할 대상이 없으니 정상 흐름이다.
                log.debug("[판매량] 갱신 대상 상품 없음 — product={}", line.productId());
            }
        }
    }
}
