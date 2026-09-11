package com.glassvue.domain.audit.repository;

import com.glassvue.domain.audit.entity.AdminAuditLog;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.entity.AuditTargetType;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
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
     * <p>🔴 <b>{@code actions} 는 비울 수 없다</b>(2026-09-11) — 「전체」는 호출측이 <b>모든 값</b>으로 넘긴다.
     * Oracle 은 빈 {@code IN ()} 을 문법 오류로 받고, «null 이면 전체» 분기를 컬렉션에 두면 방언마다 갈린다.
     * 조작 종류가 34개로 늘어 <b>여러 개를 한 번에</b> 고르게 됐다(감사 화면의 분류별 체크 드롭다운).
     *
     * <p>🔴 <b>targetType 이 생기기 전에는 회원 아닌 행을 좁힐 방법이 「조작 종류」뿐이었다</b>
     * (V50 주석이 «대가» 로 적어 둔 자리). 이제 «상품에 일어난 일 전부» 를 한 번에 볼 수 있다 —
     * 등록·수정·삭제·복구·할인 조작이 <b>같은 targetType</b> 으로 묶인다.
     */
    @Query("""
            select a from AdminAuditLog a
            where a.action in :actions
              and (:targetType is null or a.targetType = :targetType)
              and (:targetLogin is null or lower(a.targetLogin) like lower(concat('%', :targetLogin, '%')))
              and (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt < :to)
            """)
    Page<AdminAuditLog> search(@Param("actions") Collection<AuditAction> actions,
                               @Param("targetType") AuditTargetType targetType,
                               @Param("targetLogin") String targetLogin,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               Pageable pageable);

    /**
     * 기간 없이 전체를 본다.
     *
     * <p>⚠ <b>{@code ShippingPolicy} 에서 «기본값 오버로드» 를 없앤 것과 왜 다른가</b>(2026-09-01):
     * 거기서는 생략된 인자가 <b>«어떤 무료배송 기준을 적용할 것인가» 라는 결정</b>이라, 기본값을 두면
     * 🔴 <b>결정을 안 하고도 통과</b>했고 그 어긋남이 돈에서 났다.
     * 여기서 생략되는 것은 결정이 아니라 <b>«기간을 안 좁힌다» 는 중립값</b>이다 —
     * {@code null} 이 곧 «전체» 이고, 다른 뜻으로 읽힐 여지가 없다.
     */
    default Page<AdminAuditLog> search(AuditAction action, AuditTargetType targetType,
                                       String targetLogin, Pageable pageable) {
        return search(action == null ? EnumSet.allOf(AuditAction.class) : EnumSet.of(action),
                targetType, targetLogin, null, null, pageable);
    }
}
