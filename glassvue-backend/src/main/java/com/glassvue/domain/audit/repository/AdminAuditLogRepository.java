package com.glassvue.domain.audit.repository;

import com.glassvue.domain.audit.entity.AdminAuditLog;
import com.glassvue.domain.audit.entity.AuditAction;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    /**
     * 감사 이력 검색. action(조작 종류)·targetLogin(대상 loginId 부분일치)로 좁힐 수 있고, 둘 다 null 이면 전체.
     * 정렬은 호출측 Pageable 에 맡긴다(기본 최신순은 쿼리 서비스에서 채운다).
     */
    @Query("""
            select a from AdminAuditLog a
            where (:action is null or a.action = :action)
              and (:targetLogin is null or lower(a.targetLogin) like lower(concat('%', :targetLogin, '%')))
            """)
    Page<AdminAuditLog> search(@Param("action") AuditAction action,
                               @Param("targetLogin") String targetLogin,
                               Pageable pageable);
}
