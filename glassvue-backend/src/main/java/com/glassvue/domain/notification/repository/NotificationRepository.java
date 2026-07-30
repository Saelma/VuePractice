package com.glassvue.domain.notification.repository;

import com.glassvue.domain.notification.entity.Notification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** 내 알림 최신순(페이징). 알림함의 유일한 목록 조회 경로다. */
    Page<Notification> findByMemberIdOrderByCreatedAtDesc(UUID memberId, Pageable pageable);

    /** 벨 뱃지에 쓰는 안읽음 수. */
    long countByMemberIdAndReadFalse(UUID memberId);

    /** 읽음 처리는 본인 알림만 — id 로만 찾으면 남의 알림을 읽음 처리할 수 있다. */
    Optional<Notification> findByIdAndMemberId(UUID id, UUID memberId);

    /** 모두 읽음 — 안읽은 것만 벌크 UPDATE(전체 로딩·더티체킹 불필요). 반영된 행 수 반환. */
    @Modifying
    @Query("update Notification n set n.read = true where n.memberId = :memberId and n.read = false")
    int markAllRead(@Param("memberId") UUID memberId);

    /** 회원 삭제 정리용(F-1). */
    long deleteByMemberId(UUID memberId);
}
