package com.glassvue.domain.catalog.repository;

import com.glassvue.domain.catalog.entity.ProductVariant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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
     * 한 상품의 <b>총재고</b>(옵션 재고 합) — 재입고 판단(상품 총재고 0→양수)용.
     * 옵션이 없거나 전부 0이면 0. 벌크 UPDATE 직후 읽어도 되도록 스칼라 집계로 뽑는다(1차 캐시 우회).
     */
    @Query("select coalesce(sum(v.stock), 0) from ProductVariant v where v.productId = :productId")
    long sumStockByProduct(@Param("productId") UUID productId);

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

    /**
     * 재고가 임계치 <b>이하</b>인 옵션 목록 — 관리자 대시보드의 「재고 부족」 (2026-08-03, B-16).
     *
     * <p>⚠ <b>{@code HIDDEN} 상품은 뺀다.</b> 숨긴 상품은 팔리지 않으므로 재고를 채울 이유가 없다 —
     * 넣으면 "처리해야 할 것" 목록에 아무도 손댈 필요 없는 줄이 섞인다. {@code SOLD_OUT} 은
     * <b>남긴다</b>(관리자가 손으로 붙이는 표시일 뿐, 재입고가 필요한 상태인 건 그대로다).
     *
     * <p>정렬은 <b>재고 적은 순</b> — 0(품절)이 맨 위로 온다. 같은 재고면 상품명·옵션 순서로 안정화한다
     * (안 그러면 새로고침마다 줄 순서가 바뀐다).
     */
    @Query("""
            select new com.glassvue.domain.catalog.repository.LowStockVariant(
                v.productId, p.name, v.name, v.stock)
            from ProductVariant v, Product p
            where p.id = v.productId
              and p.status <> com.glassvue.domain.catalog.entity.ProductStatus.HIDDEN
              and v.stock <= :threshold
            order by v.stock asc, p.name asc, v.sortOrder asc
            """)
    List<LowStockVariant> findLowStock(@Param("threshold") long threshold, Pageable pageable);

    /**
     * 재고 부족 옵션의 <b>전체</b> 건수. 목록은 상위 몇 줄만 보여주므로 카드 숫자는 따로 센다 —
     * {@code items.size()} 를 쓰면 "10건 넘게 있는데 10으로 보이는" 거짓말이 된다.
     * 조건은 {@link #findLowStock} 과 <b>반드시 같아야 한다</b>(둘이 갈리면 숫자와 목록이 어긋난다).
     */
    @Query("""
            select count(v)
            from ProductVariant v, Product p
            where p.id = v.productId
              and p.status <> com.glassvue.domain.catalog.entity.ProductStatus.HIDDEN
              and v.stock <= :threshold
            """)
    long countLowStock(@Param("threshold") long threshold);
}
