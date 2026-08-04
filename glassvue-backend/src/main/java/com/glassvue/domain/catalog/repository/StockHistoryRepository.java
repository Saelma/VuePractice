package com.glassvue.domain.catalog.repository;

import com.glassvue.domain.catalog.entity.StockHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockHistoryRepository extends JpaRepository<StockHistory, UUID> {

    /**
     * 한 상품의 재고 이력(최신순) — <b>유일한 조회 경로</b>다.
     *
     * <p>기준이 {@code variantId} 가 아니라 {@code productId} 인 이유는 {@link StockHistory} 참조:
     * 관리자 편집이 옵션을 통째로 교체해 {@code variantId} 가 바뀌므로, 옵션 id 로 조회하면
     * 편집 한 번에 이력이 끊긴다.
     *
     * <p>{@code idx_stock_history_product (product_id, created_at)} 가 정렬까지 커버한다.
     */
    Page<StockHistory> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);
}
