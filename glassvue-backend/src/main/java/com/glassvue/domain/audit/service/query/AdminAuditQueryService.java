package com.glassvue.domain.audit.service.query;

import com.glassvue.domain.audit.dto.AdminAuditLogResponse;
import com.glassvue.domain.audit.entity.AdminAuditLog;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.entity.AuditTargetType;
import com.glassvue.domain.audit.repository.AdminAuditLogRepository;
import com.glassvue.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 이력 조회(SUPER_ADMIN 전용 — 조회 권한은 SecurityConfig 의 {@code /api/admin/audit/**} 규칙으로 건다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditQueryService {

    private final AdminAuditLogRepository auditLogRepository;

    public PageResponse<AdminAuditLogResponse> search(AuditAction action, AuditTargetType targetType,
                                                      String targetLogin, Pageable pageable) {
        String login = (targetLogin == null || targetLogin.isBlank()) ? null : targetLogin.trim();
        Page<AdminAuditLog> page =
                auditLogRepository.search(action, targetType, login, withDefaultSort(pageable));
        return PageResponse.from(page.map(AdminAuditLogResponse::from));
    }

    /** 정렬을 안 주면 최신순 — 감사 이력은 최근 것이 위에 와야 읽힌다. */
    private Pageable withDefaultSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
