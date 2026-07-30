package com.glassvue.domain.notification.repository;

import com.glassvue.domain.notification.entity.NotificationPref;
import com.glassvue.domain.notification.entity.NotificationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationPrefRepository extends JpaRepository<NotificationPref, UUID> {

    List<NotificationPref> findByMemberId(UUID memberId);
    /** 회원 삭제 정리용(F-1). */
    long deleteByMemberId(UUID memberId);

    Optional<NotificationPref> findByMemberIdAndType(UUID memberId, NotificationType type);

    /**
     * 기존 설정 값만 바꾼다(있으면). 반영된 행 수 반환 — 0이면 아직 없다는 뜻이라 호출부가 INSERT 한다.
     * find→insert 대신 update→(없으면)insert 로 가면, 재토글은 순수 UPDATE 라 유니크 경합이 안 생긴다.
     */
    @Modifying
    @Query("update NotificationPref p set p.enabled = :enabled where p.memberId = :memberId and p.type = :type")
    int updateEnabled(@Param("memberId") UUID memberId,
                      @Param("type") NotificationType type,
                      @Param("enabled") boolean enabled);
}
