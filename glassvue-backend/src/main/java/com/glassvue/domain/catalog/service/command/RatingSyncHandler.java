package com.glassvue.domain.catalog.service.command;

import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.review.event.ReviewRatingChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 집계를 상품에 반영하는 진짜 주체(Handler). 리스너는 위임만 하고 로직은 전부 여기 있다
 * — MSA 전환 시 리스너 자리에 RabbitMQ 컨슈머만 갈아끼우고 이 클래스는 그대로 재사용한다.
 * 배치 재계산(전체 상품 별점 보정) 같은 다른 진입점에서도 이 메서드를 부르면 된다.
 *
 * <p>리스너가 AFTER_COMMIT + @Async라 원 트랜잭션은 이미 끝났다 → 자체 트랜잭션이 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingSyncHandler {

    private final ProductRepository productRepository;

    /**
     * 상품의 비정규화된 별점·리뷰수를 갱신하고, 목록 캐시를 무효화한다.
     *
     * <p>캐시 무효화가 여기 있는 이유: {@code products:list}는 catalog 소유 캐시고 응답에
     * 별점이 포함되므로, 값이 바뀌면 catalog가 스스로 비워야 한다. review는 이 캐시를 모른다.
     */
    @Transactional
    @CacheEvict(cacheNames = "products:list", allEntries = true)
    public void handle(ReviewRatingChangedEvent event) {
        int updated = productRepository.updateRating(
                event.productId(), event.averageRating(), event.reviewCount());
        if (updated == 0) {
            // 리뷰 커밋과 이 처리 사이에 상품이 삭제된 경우 — 갱신할 대상이 없으니 정상 흐름이다.
            log.debug("[별점] 갱신 대상 상품 없음 — product={}", event.productId());
            return;
        }
        log.debug("[별점] 상품 집계 반영 — product={} avg={} count={}",
                event.productId(), event.averageRating(), event.reviewCount());
    }
}
