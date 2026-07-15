package com.glassvue.domain.catalog.repository;

import com.glassvue.domain.catalog.entity.Product;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID>, ProductRepositoryCustom {

    /** 재고가 충분할 때만 원자적으로 차감(오버셀 방지). 반영된 행 수 반환(0=재고 부족). */
    @Modifying
    @Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty")
    int decreaseStock(@Param("id") UUID id, @Param("qty") long qty);

    /** 재고 복원(주문 취소). */
    @Modifying
    @Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
    int increaseStock(@Param("id") UUID id, @Param("qty") long qty);
}
