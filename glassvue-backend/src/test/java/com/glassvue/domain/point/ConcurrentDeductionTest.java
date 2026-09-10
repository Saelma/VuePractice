package com.glassvue.domain.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.entity.ProductVariant;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.repository.ProductVariantRepository;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.point.repository.PointAccountRepository;
import com.glassvue.domain.point.repository.PointHistoryRepository;
import com.glassvue.domain.point.service.PointService;
import com.glassvue.global.exception.BusinessException;
import java.util.List;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 🔴 <b>같은 것을 «동시에» 빼면 두 번 나가는가</b> — BACKLOG §I-11 이 보류해 둔 논증의 실측
 * (2026-09-02).
 *
 * <p>I-11 은 이렇게 적어 뒀다: *"동시성 방어(`@Version`·`@Lock` 이 order·point 에 <b>0건</b>).
 * 🔴 <b>부재는 실측이지만 사고 시나리오는 «논증» 이다.</b> 더블클릭이면 재고·적립금이 두 번 나갈 수
 * 있다는 주장인데, <b>재현 테스트(스레드 둘)를 먼저 만들어 실측</b>하고 나서 판단한다."*
 *
 * <p>⚠ <b>애노테이션을 세는 것은 틀린 측정이었다.</b> 방어는 애노테이션에만 사는 것이 아니다 —
 * <b>SQL 에도 산다.</b> 착수 전 실측에서 둘이 갈렸다:
 * <ul>
 *   <li><b>재고</b> — {@code update … set stock = stock - :qty where id = :id and stock >= :qty}.
 *       읽고-고치고-쓰기가 아니라 <b>조건부 원자 UPDATE</b> 다. 진 쪽은 0행을 받아 {@code OUT_OF_STOCK}.</li>
 *   <li><b>적립금</b> — {@code account.use(amount)} 가 <b>엔티티 필드를 고친다</b>(더티 체킹).
 *       즉 <b>읽고-고치고-쓰기</b> 라 두 트랜잭션이 같은 잔액을 읽으면 한쪽 차감이 사라진다.</li>
 * </ul>
 *
 * <p>🔴 <b>이 테스트만 롤백을 못 쓴다</b>({@code EventCouponConcurrencyTest} 와 같은 이유) —
 * 경합은 «커밋된 것을 서로 못 보는 두 트랜잭션» 사이에서 일어나므로 한 트랜잭션 안에서 두 번
 * 부르면 <b>재현 자체가 안 된다.</b> ⚠ 그래서 <b>실제 행이 커밋된다</b>. 만든 것은
 * {@link AfterEach} 에서 되돌리고 이름에 {@code ZZ} 를 붙인다(WA §3-1).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
class ConcurrentDeductionTest {

    @Autowired PointService pointService;
    @Autowired PointAccountRepository accountRepository;
    @Autowired PointHistoryRepository historyRepository;
    @Autowired ProductCommandService productCommandService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired com.glassvue.domain.notification.repository.NotificationRepository notificationRepository;

    private UUID memberId;
    private UUID productId;
    private UUID variantId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        memberId = memberRepository.save(Member.builder()
                .loginId("zzrace_" + sfx)
                .password(passwordEncoder.encode("password123"))
                .nickname("ZZ경합" + sfx)
                .role(Role.USER).build()).getId();
        pointService.openAccount(memberId);

        categoryId = categoryRepository.save(Category.builder().name("ZZC-경합" + sfx).build()).getId();
        productId = productRepository.save(Product.builder()
                .name("ZZP-경합" + sfx).description("d").price(10_000)
                .status(ProductStatus.SELLING)
                .category(categoryRepository.findById(categoryId).orElseThrow()).build()).getId();
        // 🔴 재고 **1** — 둘이 동시에 집으면 하나는 반드시 져야 한다.
        variantId = variantRepository.save(ProductVariant.of(productId, "기본", 0, 1, 0)).getId();
    }

    /**
     * ⚠ 순서가 있다 — 참조가 남으면 FK 가 막는다(선례가 여기서 한 번 터졌다).
     *
     * <p>🔴 <b>알림도 치운다</b>(2026-09-10 · BACKLOG M-4). 이 테스트는 <b>롤백을 안 쓰고</b>
     * 실제로 커밋하므로 재고가 0이 되면 {@code StockRunningLowEvent} 가
     * {@code AFTER_COMMIT} 으로 <b>진짜 발행된다</b> — 관리자 셋에게 알림 3건이 남는다.
     * 그런데 아래에서 상품만 지우니 <b>없는 상품을 가리키는 알림</b>이 매 실행마다 3건씩 쌓였다
     * (2026-09-02 에만 39건 — 그날 이 테스트를 많이 돌렸다).
     *
     * <p>⚠ <b>운영 경로의 수정({@code ProductPurgedEvent})은 여기까지 안 닿는다</b> —
     * 그건 {@code ProductCommandService.purge()} 가 발행하는데 이 테스트는
     * {@code productRepository.deleteById} 로 <b>서비스를 건너뛰기</b> 때문이다.
     * 🔴 <b>테스트가 만든 것은 테스트가 치운다.</b>
     *
     * <p>⚠ 알림 수신자는 <b>이 테스트의 회원이 아니라 관리자들</b>이라
     * {@code deleteByMemberId} 로는 안 잡힌다 — 상품 링크로 지워야 한다.
     */
    @AfterEach
    void tearDown() {
        awaitAndDeleteStockAlerts();
        transactionTemplate.executeWithoutResult(status -> {
            historyRepository.deleteAll(
                    historyRepository.findByMemberIdOrderByCreatedAtDesc(memberId, Pageable.unpaged()).getContent());
            accountRepository.findByMemberId(memberId).ifPresent(accountRepository::delete);
            variantRepository.deleteById(variantId);
            productRepository.deleteById(productId);
            categoryRepository.deleteById(categoryId);
            memberRepository.deleteById(memberId);
        });
    }

    /**
     * 재고 알림이 올 때까지 <b>기다렸다가</b> 지운다.
     *
     * <p>🔴 <b>그냥 지우면 샌다.</b> {@code StockRunningLowEvent} 리스너는 {@code @Async} 라
     * <b>다른 스레드</b>에서 {@code AFTER_COMMIT} 뒤에 쓴다 — {@code @AfterEach} 가 먼저 돌면
     * 지울 것이 아직 없고, 알림은 <b>그 뒤에</b> 태어나 고아로 남는다.
     * ⚠ 2026-09-10 에 실제로 그랬다: 정리를 넣고 전수를 돌렸는데 <b>한 건이 새어</b> 남았다
     * (55건을 치운 바로 그 실행에서 1건이 새로 생겼다).
     *
     * <p>⚠ <b>알림이 안 오는 시험도 있다</b>(재고를 안 건드리는 적립금 경합). 그래서 «올 때까지»
     * 가 아니라 <b>예산을 두고</b> 기다린다 — 하나라도 지웠으면 바로 끝내고, 없으면 예산만 쓰고 나간다.
     */
    private void awaitAndDeleteStockAlerts() {
        for (int i = 0; i < 20; i++) {          // 100ms × 20 = 최대 2초
            Integer deleted = transactionTemplate.execute(
                    st -> notificationRepository.deleteByProductLink(productId.toString()));
            if (deleted != null && deleted > 0) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** 두 스레드를 같은 순간에 푼다. 반환: [성공 수, 거부(BusinessException) 수]. */
    private int[] race(Runnable action) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Callable<Void> task = () -> {
            start.await();
            try {
                action.run();
                ok.incrementAndGet();
            } catch (BusinessException e) {
                rejected.incrementAndGet();
            }
            return null;
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Void>> fs = List.of(pool.submit(task), pool.submit(task));
            start.countDown();
            for (Future<Void> f : fs) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return new int[] { ok.get(), rejected.get() };
    }

    private long stockNow() {
        return variantRepository.findById(variantId).orElseThrow().getStock();
    }

    private long balanceNow() {
        return accountRepository.findByMemberId(memberId).orElseThrow().getBalance();
    }

    /** 원장 합 — 불변식 ⑦(`scripts/check-money-invariants.sh`)과 **같은 질문**이다. */
    private long ledgerSum() {
        return historyRepository.findByMemberIdOrderByCreatedAtDesc(memberId, Pageable.unpaged())
                .getContent().stream().mapToLong(h -> h.getAmount()).sum();
    }

    @Test
    @DisplayName("🔴 재고 — 마지막 1개를 둘이 동시에 집으면 **정확히 하나만** 나간다 (SQL 가드가 막는다)")
    void concurrentStockDecrease_onlyOneWins() throws Exception {
        int[] r = race(() -> productCommandService.decreaseStock(variantId, 1, null));

        // ⚠ 애노테이션은 0건인데 **SQL 의 `where stock >= :qty` 가 방어다.**
        //    「@Version 이 없다 → 위험하다」가 논증이었고, 여기서 그 논증이 갈린다.
        assertThat(r[0]).as("성공").isEqualTo(1);
        assertThat(r[1]).as("OUT_OF_STOCK").isEqualTo(1);
        assertThat(stockNow()).as("재고가 음수로 안 내려간다").isZero();
    }

    @Test
    @DisplayName("🔴 적립금 — 같은 잔액을 둘이 동시에 쓰면 **원장과 잔액이 어긋나는가**")
    void concurrentPointUse_keepsLedgerConsistent() throws Exception {
        // 잔액을 10,000 으로 심는다. ⚠ 이력 없이 심으므로 **절대값이 아니라 «변화량» 을 본다** —
        //    그래야 시드 방식이 단언을 오염시키지 않는다.
        transactionTemplate.executeWithoutResult(s -> {
            var account = accountRepository.findByMemberId(memberId).orElseThrow();
            account.earn(10_000);
            accountRepository.save(account);
        });
        long balanceBefore = balanceNow();
        long ledgerBefore = ledgerSum();
        assertThat(balanceBefore).isEqualTo(10_000);

        // 둘 다 «전액» 을 쓰려 한다. 하나만 되어야 한다.
        int[] r = race(() -> pointService.use(memberId, 10_000, 10_000, null));

        // 🔴 **이 단언이 이 파일의 본체다.** 둘 다 성공하면 잔액은 0 인데 원장에는 −20,000 이 적혀
        //    「잔액 변화 = 원장 변화」(불변식 ⑦)가 깨진다 — 고객이 10,000원을 **두 번** 쓴 것이다.
        assertThat(balanceNow() - balanceBefore)
                .as("잔액 변화 = 원장 변화 (불변식 ⑦ — 성공 %d · 거부 %d)", r[0], r[1])
                .isEqualTo(ledgerSum() - ledgerBefore);
        assertThat(balanceNow()).as("잔액이 음수가 아니다").isGreaterThanOrEqualTo(0);
    }

    /*
     * ⚠ **되돌려서 확인 (2026-09-02)** — 변형 셋 중 둘이 잡혔다:
     *   U1 잠금 없는 finder 로 되돌림                    → ✅ 잡힘 (원래 버그)
     *   U3 `@Lock` 을 통째로 제거                        → ✅ 잡힘
     *   U2 `PESSIMISTIC_WRITE` → `PESSIMISTIC_READ`      → ❌ 안 잡힘
     *
     * ✅ **U2 가 안 잡힌 이유는 같은 날 실측했다** (`LockModeSqlProbeTest`):
     * Oracle 방언이 **두 모드에 같은 절을 내보낸다** — 둘 다 ` for update` 다.
     * 🔴 **즉 U2 는 «테스트가 약해서» 안 잡힌 것이 아니라 «바꿀 것이 없어서» 안 잡혔다.**
     *
     * ⚠ **처음엔 «확인 안 함» 으로 적어 뒀었다** — SQL 로깅을 켜려다 두 번 실패했기 때문이다
     * (`--info` 로도, `LOGGING_LEVEL_ORG_HIBERNATE_SQL` 로도 안 켜졌다).
     * 🔴 **물어볼 곳을 틀리게 잡고 있었다**: 실행 SQL 을 로그에서 «건져 올리려» 했는데
     * **방언에 직접 물으면 됐다**(`Dialect#getReadLockString`). **경합을 만들 필요도 없었다.**
     * → 「확인 못 했다」는 「확인할 수 없다」가 아니라 **「내가 고른 방법으로 못 했다」** 였다.
     */
}
