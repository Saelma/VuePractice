package com.glassvue.domain.audit.event;

import com.glassvue.domain.audit.entity.AuditAction;
import java.util.UUID;

/**
 * 관리자 조작이 일어났음을 알리는 도메인 이벤트 — 감사 기록의 <b>공개 계약</b>이다.
 *
 * <p>다른 도메인(member 등)은 audit 의 내부 구현을 직접 부르지 않고 이 이벤트만 발행한다
 * (도메인 간 직접 참조 금지 — CLAUDE.md). audit 은 {@code AdminAuditListener} 로 받는다.
 *
 * <p>기본 {@code @EventListener} 는 발행 시점에 <b>동기·같은 트랜잭션</b>으로 처리된다. 그래서 감사 기록이
 * 실패하면 조작 자체가 롤백되고, 조작이 롤백되면 감사도 남지 않는다("조작 없이 감사 없고, 감사 없이 조작 없다").
 *
 * @param action      조작 종류
 * @param actorId     행위자(관리자) id
 * @param actorName   행위자 닉네임 스냅샷
 * @param targetId    대상 회원 id
 * @param targetLogin 대상 loginId 스냅샷
 * @param detail      부가 설명(역할변경의 전/후 등). 없으면 null.
 */
public record AdminActionEvent(
        AuditAction action,
        UUID actorId,
        String actorName,
        UUID targetId,
        String targetLogin,
        String detail
) {
}
