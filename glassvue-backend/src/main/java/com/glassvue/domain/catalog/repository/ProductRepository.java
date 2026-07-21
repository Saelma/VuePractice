package com.glassvue.domain.catalog.repository;

import com.glassvue.domain.catalog.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID>, ProductRepositoryCustom {

    /** 카테고리 삭제 가능 여부 판단용 — 해당 카테고리에 속한 상품이 하나라도 있는지. */
    boolean existsByCategoryId(UUID categoryId);

    /** 재고가 충분할 때만 원자적으로 차감(오버셀 방지). 반영된 행 수 반환(0=재고 부족). */
    @Modifying
    @Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty")
    int decreaseStock(@Param("id") UUID id, @Param("qty") long qty);

    /** 재고 복원(주문 취소). */
    @Modifying
    @Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
    int increaseStock(@Param("id") UUID id, @Param("qty") long qty);

    /**
     * 리뷰 집계 비정규화 컬럼 갱신 — ReviewRatingChangedEvent 수신 시.
     * 상품 전체를 로딩·더티체킹할 이유가 없어 벌크 UPDATE로 두 컬럼만 건드린다.
     * 반영된 행 수 반환(0 = 이미 삭제된 상품).
     */
    @Modifying
    @Query("update Product p set p.avgRating = :avg, p.reviewCount = :cnt where p.id = :id")
    int updateRating(@Param("id") UUID id, @Param("avg") double avg, @Param("cnt") long cnt);

    /** 차감 직후 잔여재고 확인용 — 벌크 UPDATE가 1차 캐시를 안 고치므로 스칼라 프로젝션으로 DB를 직접 읽는다. */
    @Query("select new com.glassvue.domain.catalog.repository.StockSnapshot(p.name, p.stock)"
            + " from Product p where p.id = :id")
    Optional<StockSnapshot> findStockSnapshot(@Param("id") UUID id);
}
