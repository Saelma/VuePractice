package com.glassvue.domain.point.repository;

import com.glassvue.domain.point.entity.PointAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointAccountRepository extends JpaRepository<PointAccount, UUID> {

    /** 조회용. ⚠ <b>잔액을 바꿀 거면 {@link #findByMemberIdForUpdate} 를 쓴다.</b> */
    Optional<PointAccount> findByMemberId(UUID memberId);

    /**
     * 🔴 <b>잔액을 바꾸기 전에 그 행을 잠근다</b> (2026-09-02, BACKLOG §I-11).
     *
     * <p><b>왜</b>: 잔액을 바꾸는 다섯(`use`·`earn`·`refund`·`addPurchase`·`subtractPurchase`)이
     * 전부 <b>엔티티 필드를 고치는</b> 방식이라 «읽고-고치고-쓰기» 다. 두 트랜잭션이 같은 잔액을
     * 읽으면 <b>한쪽 차감이 사라진다.</b>
     * ⚠ <b>실측(2026-09-02)</b>: 잔액 10,000 에 10,000 사용을 두 스레드로 동시에 걸었더니
     * <b>둘 다 성공</b>했다 — 원장에는 −20,000 이 적히고 잔액은 −10,000 만 줄어,
     * <b>「잔액 = 원장 합」(점검 스크립트 ⑦)이 깨졌다.</b> 고객이 10,000원을 두 번 쓴 것이다.
     * 재현은 {@code ConcurrentDeductionTest}.
     *
     * <p>⚠ <b>재고는 이 문제가 없다</b> — 거기는 {@code where stock >= :qty} 라는
     * <b>조건부 원자 UPDATE</b> 라 방어가 SQL 에 있다. 🔴 <b>그래서 «`@Version`·`@Lock` 이 0건이다»
     * 로 위험을 판정한 것이 틀린 측정이었다</b> — 방어는 애노테이션에만 살지 않는다.
     *
     * <p><b>왜 낙관적 락(`@Version`)이 아닌가</b>: 그쪽은 <b>컬럼이 늘어 마이그레이션이 따라온다.</b>
     * 지금 필요한 것은 «충돌을 알리기» 가 아니라 «차감을 줄 세우기» 이고, 트래픽이 없어
     * 잠금 경합의 비용이 사실상 0 이다. 필요해지면 그때 바꾼다.
     *
     * <p>⚠ <b>조회 경로와 반드시 갈라 둔다</b> — {@code myAccount}·{@code balanceOf}·{@code gradeOf}
     * 는 {@code @Transactional(readOnly = true)} 라 여기에 걸리면 안 된다.
     * 🔴 <b>그래서 기존 {@code findByMemberId} 에 애노테이션을 붙이지 않고 메서드를 하나 더 뒀다.</b>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PointAccount a where a.memberId = :memberId")
    Optional<PointAccount> findByMemberIdForUpdate(@Param("memberId") UUID memberId);

    /** 회원 삭제 정리용(F-1). 회원당 최대 1건이다. */
    long deleteByMemberId(UUID memberId);
}
