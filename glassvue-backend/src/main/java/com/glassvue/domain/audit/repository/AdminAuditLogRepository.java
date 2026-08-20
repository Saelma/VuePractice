package com.glassvue.domain.audit.repository;

import com.glassvue.domain.audit.entity.AdminAuditLog;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.entity.AuditTargetType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    /**
     * 감사 이력 검색. action(조작 종류)·targetType(대상 종류)·targetLogin(대상 loginId 부분일치)로
     * 좁힐 수 있고, 셋 다 null 이면 전체. 정렬은 호출측 Pageable 에 맡긴다(기본 최신순은 쿼리 서비스에서).
     *
     * <p>🔴 <b>targetType 이 생기기 전에는 회원 아닌 행을 좁힐 방법이 「조작 종류」뿐이었다</b>
     * (V50 주석이 «대가» 로 적어 둔 자리). 이제 «상품에 일어난 일 전부» 를 한 번에 볼 수 있다 —
     * 등록·수정·삭제·복구·할인 조작이 <b>같은 targetType</b> 으로 묶인다.
     */
    @Query("""
            select a from AdminAuditLog a
            where (:action is null or a.action = :action)
              and (:targetType is null or a.targetType = :targetType)
              and (:targetLogin is null or lower(a.targetLogin) like lower(concat('%', :targetLogin, '%')))
            """)
    Page<AdminAuditLog> search(@Param("action") AuditAction action,
                               @Param("targetType") AuditTargetType targetType,
                               @Param("targetLogin") String targetLogin,
                               Pageable pageable);
}
