package com.glassvue.domain.audit.dto;

import com.glassvue.domain.audit.entity.AdminAuditLog;
import com.glassvue.domain.audit.entity.AuditAction;
import java.time.Instant;
import java.util.UUID;

/** 감사 이력 응답(SUPER_ADMIN 조회용). 스냅샷 이름을 그대로 실어 대상이 사라져도 읽을 수 있다. */
public record AdminAuditLogResponse(
        UUID id,
        AuditAction action,
        UUID actorId,
        String actorName,
        UUID targetId,
        String targetLogin,
        String detail,
        Instant createdAt
) {
    public static AdminAuditLogResponse from(AdminAuditLog log) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getActorId(),
                log.getActorName(),
                log.getTargetId(),
                log.getTargetLogin(),
                log.getDetail(),
                log.getCreatedAt());
    }
}
