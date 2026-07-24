package com.glassvue.domain.point.repository;

import com.glassvue.domain.point.entity.PointHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointHistoryRepository extends JpaRepository<PointHistory, UUID> {

    /** 내 적립금 이력 — 최신순. V21 의 (member_id, created_at) 인덱스를 탄다. */
    Page<PointHistory> findByMemberIdOrderByCreatedAtDesc(UUID memberId, Pageable pageable);

    List<PointHistory> findByOrderId(UUID orderId);

    /**
     * 이력의 합 — <b>잔액 검증용</b>이다.
     *
     * <p>이력이 원장이고 {@code point_account.balance} 는 그 캐시라, 둘이 갈라지면 사고다.
     * 통합테스트가 매번 이걸로 대조한다(잔액만 맞추고 이력을 안 남기는 코드를 잡는다).
     */
    @Query("select coalesce(sum(h.amount), 0) from PointHistory h where h.memberId = :memberId")
    long sumAmountByMemberId(@Param("memberId") UUID memberId);
}
