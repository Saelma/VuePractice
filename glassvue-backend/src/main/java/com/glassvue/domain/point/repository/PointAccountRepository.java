package com.glassvue.domain.point.repository;

import com.glassvue.domain.point.entity.PointAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointAccountRepository extends JpaRepository<PointAccount, UUID> {

    Optional<PointAccount> findByMemberId(UUID memberId);
    /** 회원 삭제 정리용(F-1). 회원당 최대 1건이다. */
    long deleteByMemberId(UUID memberId);
}
