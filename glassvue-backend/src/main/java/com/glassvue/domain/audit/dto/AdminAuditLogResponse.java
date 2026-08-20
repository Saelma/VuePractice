package com.glassvue.domain.audit.dto;

import com.glassvue.domain.audit.entity.AdminAuditLog;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.entity.AuditTargetType;
import java.time.Instant;
import java.util.UUID;

/** 감사 이력 응답(SUPER_ADMIN 조회용). 스냅샷 이름을 그대로 실어 대상이 사라져도 읽을 수 있다. */
public record AdminAuditLogResponse(
        UUID id,
        AuditAction action,
        /** 🔴 targetId 가 무엇의 id 인지(V53). 화면이 「대상」 열을 어떻게 그릴지 이걸로 가른다. */
        AuditTargetType targetType,
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
                log.getTargetType(),
                log.getActorId(),
                log.getActorName(),
                log.getTargetId(),
                log.getTargetLogin(),
                log.getDetail(),
                log.getCreatedAt());
    }
}
