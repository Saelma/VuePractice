package com.glassvue.domain.notification.repository;

import com.glassvue.domain.notification.entity.NotificationPref;
import com.glassvue.domain.notification.entity.NotificationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPrefRepository extends JpaRepository<NotificationPref, UUID> {

    List<NotificationPref> findByMemberId(UUID memberId);

    Optional<NotificationPref> findByMemberIdAndType(UUID memberId, NotificationType type);
}
