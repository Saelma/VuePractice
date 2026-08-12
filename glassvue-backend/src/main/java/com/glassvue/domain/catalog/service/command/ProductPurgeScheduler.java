package com.glassvue.domain.catalog.service.command;

import com.glassvue.domain.catalog.config.CatalogProperties;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.repository.ProductRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유예가 지난 「삭제 대기」 상품을 <b>진짜로</b> 지운다 (2026-08-12, BACKLOG F-7).
 *
 * <p>{@code ImageCleanupScheduler} 와 <b>같은 모양</b>이다 — 유예 시간 + {@code @Scheduled} +
 * on/off 스위치 + 처리 건수 로그. Spring Batch 는 아직 도입 전이고 이 정도엔 과하다.
 * 다중 인스턴스가 되면 중복 실행되므로 그때는 분산 락이나 배치 잡으로 옮긴다.
 *
 * <p>🔴 <b>여기가 되돌릴 수 없는 구간의 입구다.</b> 지우면 FK CASCADE 로 옵션·재고 이력이 함께
 * 사라지고 이미지도 지워진다. 그래서 이 클래스가 지키는 것이 둘 있다:
 * <ul>
 *   <li><b>경과 시간으로만</b> 고른다({@code deleted_at < now - grace}) — 「대기 중」이라고 다 지우지 않는다.</li>
 *   <li><b>한 건씩 지운다.</b> 하나가 터져도 나머지는 지워지고, <b>어느 상품에서 터졌는지 로그에 남는다.</b>
 *       벌크 delete 면 실패가 «몇 건 실패» 로만 보여 다음에 손댈 곳을 못 찾는다.</li>
 * </ul>
 *
 * <p>⚠ <b>지운 건수가 0이면 로그를 안 남긴다</b>(대부분의 실행이 0이다 — 이미지 정리와 같은 판단).
 * 반대로 <b>지웠으면 반드시 남긴다</b>: 되돌릴 수 없는 일이라 «언제 무엇이 사라졌나» 가 유일한 흔적이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductPurgeScheduler {

    private final ProductRepository productRepository;
    private final ProductCommandService productCommandService;
    private final CatalogProperties catalogProperties;

    /**
     * 1시간마다. 기동 직후 바로 돌지 않도록 지연을 둔다(검증 시 짧게 덮어쓸 수 있게 property 로).
     *
     * <p>⚠ 유예가 <b>일</b> 단위인데 주기가 <b>시간</b> 단위인 것은 낭비가 아니다 —
     * 주기를 하루로 두면 «7일 지났는데 최대 하루 더 남아 있는» 창이 생기고, 그 사이 목록에
     * 안 보이는 상품이 DB 에는 있는 상태가 길어진다.
     */
    @Scheduled(fixedDelayString = "${catalog.purge-interval-ms:3600000}",
               initialDelayString = "${catalog.purge-initial-delay-ms:300000}")
    @Transactional
    public void sweep() {
        if (!catalogProperties.purgeEnabled()) {
            return;
        }
        Instant threshold = Instant.now().minus(Duration.ofDays(catalogProperties.purgeGraceDays()));
        List<Product> targets = productRepository.findPurgeTargets(threshold);
        if (targets.isEmpty()) {
            return;
        }

        int purged = 0;
        for (Product product : targets) {
            try {
                productCommandService.purge(product.getId());
                purged++;
            } catch (RuntimeException e) {
                // 한 건이 터져도 나머지는 지운다. ⚠ **무엇이 터졌는지**를 남긴다 —
                // 건수만 남기면 다음에 열어 볼 곳이 없다.
                log.error("[상품] 영구 삭제 실패 — id={} name={} ({})",
                        product.getId(), product.getName(), e.toString());
            }
        }
        log.info("[상품] 유예 경과 {}건 영구 삭제 (기준: {} 이전 삭제 · 유예 {}일)",
                purged, threshold, catalogProperties.purgeGraceDays());
    }
}
