package com.glassvue.domain.wishlist.repository;

import com.glassvue.domain.wishlist.entity.Wishlist;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistRepository extends JpaRepository<Wishlist, UUID> {

    /** 내 찜 목록 — 최근에 찜한 것부터. UNIQUE(member_id, product_id) 인덱스의 선두 컬럼을 탄다. */
    List<Wishlist> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    boolean existsByMemberIdAndProductId(UUID memberId, UUID productId);

    /** 해제. 반환값(삭제 건수)으로 "찜한 적 없는 상품 해제"를 구분한다. */
    long deleteByMemberIdAndProductId(UUID memberId, UUID productId);

    /**
     * 내가 찜한 상품 id 집합 — 화면이 목록·상세에서 하트를 채울지 판단하는 데 쓴다.
     *
     * <p>엔티티가 아니라 <b>id 스칼라만</b> 뽑는다. 하트 표시에 필요한 건 id 뿐이고,
     * 엔티티를 다 읽으면 영속성 컨텍스트만 채운다.
     */
    @Query("select w.productId from Wishlist w where w.memberId = :memberId")
    List<UUID> findProductIdsByMemberId(@Param("memberId") UUID memberId);
}
