package com.glassvue.domain.audit.service.command;

import com.glassvue.domain.audit.entity.AdminAuditLog;
import com.glassvue.domain.audit.event.AdminActionEvent;
import com.glassvue.domain.audit.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 기록의 <b>실제 주체</b>(event-3layer 의 Handler). 리스너는 위임만 하고, 저장은 여기서 한다.
 *
 * <p>{@code @Transactional} 은 기본 전파(REQUIRED)라 발행측(예: 회원 정지) 트랜잭션에 <b>합류</b>한다 —
 * 별도 tx 를 열지 않는다. 그래서 감사 저장이 실패하면 조작 전체가 함께 롤백된다(감사 무결성).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminAuditCommandService {

    private final AdminAuditLogRepository auditLogRepository;

    public void record(AdminActionEvent event) {
        auditLogRepository.save(AdminAuditLog.builder()
                .action(event.action())
                .actorId(event.actorId())
                .actorName(event.actorName())
                .targetId(event.targetId())
                .targetLogin(event.targetLogin())
                .detail(event.detail())
                .build());
        log.info("Admin audit recorded: action={} target={} by actor={}",
                event.action(), event.targetId(), event.actorId());
    }
}
