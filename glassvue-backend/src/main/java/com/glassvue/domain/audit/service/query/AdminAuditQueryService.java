package com.glassvue.domain.audit.service.query;

import com.glassvue.domain.audit.dto.AdminAuditLogResponse;
import com.glassvue.domain.audit.entity.AdminAuditLog;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.entity.AuditTargetType;
import com.glassvue.domain.audit.repository.AdminAuditLogRepository;
import com.glassvue.global.common.KstDates;
import com.glassvue.global.response.PageResponse;
import java.time.LocalDate;
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

    /**
     * ⚠ <b>{@code from}·{@code to} 는 날짜다</b>(B-26) — 경계는 {@link KstDates} 가 만든다.
     * 화면이 {@code Instant} 를 보내면 「그 날의 00:00 이 언제인가」가 두 곳에서 계산된다.
     *
     * <p>🔴 <b>{@code to} 는 그 날을 포함한다</b> — 다음 날 00:00 <b>미만</b>으로 옮긴다.
     * {@code 23:59:59} 로 자르면 그 날 {@code 23:59:59.5} 에 남은 감사 한 줄이 빠지는데,
     * <b>감사 로그에서 «그 한 줄» 이 빠지는 것이 이 화면에서 가장 나쁜 결과다.</b>
     *
     * <p>⚠ <b>기간에 상한을 두지 않았다.</b> 매출(§B-26)이 366일 상한을 둔 이유는
     * «빈 날을 채워 막대를 그리기» 때문이고, 여기는 <b>페이징된 목록</b>이라 기간이 길어도
     * 한 쪽 크기는 그대로다. 🔴 <b>상한은 기간의 성질이 아니라 «그리는 방식» 의 성질이다.</b>
     */
    public PageResponse<AdminAuditLogResponse> search(AuditAction action, AuditTargetType targetType,
                                                      String targetLogin, LocalDate from, LocalDate to,
                                                      Pageable pageable) {
        String login = (targetLogin == null || targetLogin.isBlank()) ? null : targetLogin.trim();
        Page<AdminAuditLog> page = auditLogRepository.search(
                action, targetType, login,
                from == null ? null : KstDates.startOfDay(from),
                to == null ? null : KstDates.startOfNextDay(to),
                withDefaultSort(pageable));
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
