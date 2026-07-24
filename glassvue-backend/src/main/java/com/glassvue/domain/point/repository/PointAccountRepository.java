package com.glassvue.domain.point.repository;

import com.glassvue.domain.point.entity.PointAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointAccountRepository extends JpaRepository<PointAccount, UUID> {

    Optional<PointAccount> findByMemberId(UUID memberId);
}
