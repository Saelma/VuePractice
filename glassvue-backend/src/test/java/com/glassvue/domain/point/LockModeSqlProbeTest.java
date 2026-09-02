package com.glassvue.domain.point;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ⚠ <b>«확인 안 함» 을 «확인함» 으로 바꾸는 탐침</b> (2026-09-02, §I-11-1 후속).
 *
 * <p>{@code ConcurrentDeductionTest} 의 되돌려서 확인에서 <b>U2 만 안 잡혔다</b>:
 * {@code PESSIMISTIC_WRITE} 를 {@code PESSIMISTIC_READ} 로 낮춰도 테스트가 초록이었다.
 * «Oracle 에 공유 행 잠금이 없어 같은 SQL 이라서» 라고 <b>짐작</b>했지만 실측하지 않았고,
 * 그대로 두면 다음 사람이 그 짐작을 근거로 읽는다.
 *
 * <p>🔴 <b>그래서 방언에 직접 묻는다.</b> 두 모드가 어떤 잠금 절을 내보내는지는
 * {@link Dialect#getReadLockString}·{@link Dialect#getWriteLockString} 가 답한다 —
 * 로그를 켤 필요도, 실제 경합을 만들 필요도 없다.
 *
 * <p>⚠ <b>이 테스트가 지키는 것은 «두 모드가 같다» 가 아니라 «그 사실을 우리가 알고 있다» 다.</b>
 * 방언이 바뀌어(다른 DB·Hibernate 판올림) 둘이 갈리면 여기가 빨개지고,
 * 그때는 <b>U2 가 진짜 구멍이 된다</b> — 그 신호를 받으려고 둔다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
class LockModeSqlProbeTest {

    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("🔴 Oracle 에서 읽기 잠금과 쓰기 잠금이 **같은 절**로 나가는가 — U2 가 안 잡힌 이유")
    void readAndWriteLockEmitTheSameClause() {
        Dialect dialect = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect();

        String read = dialect.getReadLockString(-1);
        String write = dialect.getWriteLockString(-1);

        System.out.println("[잠금 절] PESSIMISTIC_READ  → " + read);
        System.out.println("[잠금 절] PESSIMISTIC_WRITE → " + write);

        // 🔴 같다면 U2 가 «테스트가 약해서» 안 잡힌 것이 아니라 «바꿀 것이 없어서» 안 잡힌 것이다.
        assertThat(read).isEqualTo(write);
        assertThat(write).containsIgnoringCase("for update");
    }

    @Test
    @DisplayName("⚠ 두 모드가 실제로 도는가 — 절이 «붙는 것» 과 «DB 가 받는 것» 은 다르다")
    void bothModesActuallyExecute() {
        // ⚠ **트랜잭션이 있어야 한다** — 비관적 잠금은 트랜잭션 밖에서
        //    `TransactionRequiredException` 으로 죽는다(처음에 이걸로 한 번 빨개졌다).
        //    🔴 그 자체가 정보다: **잠금은 트랜잭션 경계에 매달려 있다.**
        for (LockModeType mode : new LockModeType[] {
                LockModeType.PESSIMISTIC_READ, LockModeType.PESSIMISTIC_WRITE }) {
            transactionTemplate.executeWithoutResult(status -> {
                // 없는 id 라 결과는 비지만, 잠금 절이 붙은 SQL 을 **Oracle 이 받는지**가 확인된다.
                assertThat(entityManager
                        .createQuery("select a from PointAccount a where a.memberId = :id")
                        .setParameter("id", UUID.randomUUID())
                        .setLockMode(mode)
                        .getResultList()).isEmpty();
            });
        }
    }
}
