package com.glassvue.domain.catalog.repository;

import com.glassvue.domain.catalog.entity.ProductVariant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    /** 한 상품의 옵션 목록(관리자 정렬 순). */
    List<ProductVariant> findByProductIdOrderBySortOrderAscCreatedAtAsc(UUID productId);

    /** 여러 상품의 옵션을 한 번에 — 목록·장바구니에서 상품별 옵션을 붙일 때(N+1 회피). */
    List<ProductVariant> findByProductIdInOrderBySortOrderAscCreatedAtAsc(Collection<UUID> productIds);

    long countByProductId(UUID productId);

    /**
     * 옵션 재고를 원자적으로 차감한다 — 재고보다 많이 주문하면 0행(차감 안 됨).
     *
     * <p>재고가 {@code product} 에서 옵션으로 내려왔으므로, 예전 {@code Product.decreaseStock} 이
     * 하던 일을 여기가 한다. 벌크 UPDATE라 1차 캐시를 갱신하지 않는 점도 같다
     * (그래서 차감 후 값은 {@link #findStockSnapshot} 스칼라 프로젝션으로 읽는다).
     */
    @Modifying
    @Query("update ProductVariant v set v.stock = v.stock - :qty where v.id = :id and v.stock >= :qty")
    int decreaseStock(@Param("id") UUID id, @Param("qty") long qty);

    /** 취소 시 복원. 옵션이 이미 삭제됐으면 0행(조용히 무시). */
    @Modifying
    @Query("update ProductVariant v set v.stock = v.stock + :qty where v.id = :id")
    int increaseStock(@Param("id") UUID id, @Param("qty") long qty);

    /**
     * 차감 직후의 재고 스냅샷 — 재고 부족 알림 판단용.
     *
     * <p>상품명·옵션명을 함께 뽑아 알림이 "무선키보드 (검정/M) 재고 3" 처럼 어느 옵션인지 말할 수 있게 한다.
     * 엔티티가 아니라 스칼라 프로젝션인 이유는 벌크 UPDATE 뒤 stale 을 피하려는 것(예전과 같다).
     */
    @Query("""
            select new com.glassvue.domain.catalog.repository.VariantStockSnapshot(
                v.productId, p.name, v.name, v.stock)
            from ProductVariant v, Product p
            where v.id = :id and p.id = v.productId
            """)
    Optional<VariantStockSnapshot> findStockSnapshot(@Param("id") UUID id);
}
