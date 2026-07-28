package com.glassvue.domain.audit.service;

import com.glassvue.domain.audit.event.AdminActionEvent;
import com.glassvue.domain.audit.service.command.AdminAuditCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * {@link AdminActionEvent} 를 받아 감사 기록으로 잇는 <b>어댑터</b>(event-3layer 의 Listener) — 위임만 한다.
 *
 * <p>기본 {@code @EventListener} 라 발행 시점에 동기로 실행되어 발행측 트랜잭션 안에서 저장된다. 로직은
 * 두지 않는다(진짜 주체는 {@link AdminAuditCommandService}).
 */
@Component
@RequiredArgsConstructor
public class AdminAuditListener {

    private final AdminAuditCommandService auditCommandService;

    @EventListener
    public void on(AdminActionEvent event) {
        auditCommandService.record(event);
    }
}
