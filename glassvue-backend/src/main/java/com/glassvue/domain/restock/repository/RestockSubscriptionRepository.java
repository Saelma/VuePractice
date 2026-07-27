package com.glassvue.domain.restock.repository;

import com.glassvue.domain.restock.entity.RestockSubscription;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestockSubscriptionRepository extends JpaRepository<RestockSubscription, UUID> {

    boolean existsByMemberIdAndProductId(UUID memberId, UUID productId);

    /** 신청 취소. 반환값(삭제 건수)으로 "신청한 적 없는 상품 취소"를 구분한다(멱등). */
    long deleteByMemberIdAndProductId(UUID memberId, UUID productId);

    /**
     * 내가 재입고 신청한 상품 id 집합 — 상품 상세에서 버튼 상태(신청함/안함)를 판단하는 용도.
     * 위시리스트와 같은 감각으로, 엔티티가 아니라 id 스칼라만 뽑는다.
     */
    @Query("select s.productId from RestockSubscription s where s.memberId = :memberId")
    List<UUID> findProductIdsByMemberId(@Param("memberId") UUID memberId);

    /** 한 상품의 신청자(member id) 목록 — 재입고 이벤트 발화 시 이들에게 알림을 만든다. */
    @Query("select s.memberId from RestockSubscription s where s.productId = :productId")
    List<UUID> findMemberIdsByProductId(@Param("productId") UUID productId);

    /** 발송 후 해당 상품 구독을 통째로 비운다 — 재입고는 일회성 알림이라 한 번 보내면 신청은 끝난다. */
    long deleteByProductId(UUID productId);
}
