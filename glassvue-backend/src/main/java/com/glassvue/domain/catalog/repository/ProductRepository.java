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

    // 재고 차감/복원/스냅샷은 ProductVariantRepository 로 옮겼다 (2026-07-24, C-8) — 재고가 옵션 단위가 됐다.

    /**
     * 리뷰 집계 비정규화 컬럼 갱신 — ReviewRatingChangedEvent 수신 시.
     * 상품 전체를 로딩·더티체킹할 이유가 없어 벌크 UPDATE로 두 컬럼만 건드린다.
     * 반영된 행 수 반환(0 = 이미 삭제된 상품).
     */
    @Modifying
    @Query("update Product p set p.avgRating = :avg, p.reviewCount = :cnt where p.id = :id")
    int updateRating(@Param("id") UUID id, @Param("avg") double avg, @Param("cnt") long cnt);

    /**
     * 판매량 비정규화 컬럼 증감 — 주문/취소/반품 이벤트 수신 시. delta 는 음수(취소·반품)일 수 있다.
     * updateRating 과 같은 이유로 벌크 UPDATE 다(상품 전체 로딩·더티체킹 불필요).
     *
     * <p>음수로 내려가지 않게 CASE 로 0에서 막는다 — @Async best-effort 라 이벤트가 유실·중복되면
     * 합이 어긋날 수 있는데, 판매량이 음수면 인기순 정렬이 이상해진다(잔액 CHECK 와 같은 방어선).
     * 반영된 행 수 반환(0 = 이미 삭제된 상품).
     */
    @Modifying
    @Query("update Product p set p.soldCount = case when p.soldCount + :delta < 0 then 0 else p.soldCount + :delta end where p.id = :id")
    int addSoldCount(@Param("id") UUID id, @Param("delta") long delta);
}
