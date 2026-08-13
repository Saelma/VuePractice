package com.glassvue.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassvue.domain.coupon.entity.Coupon;
import com.glassvue.domain.coupon.entity.DiscountType;
import com.glassvue.domain.coupon.entity.MemberCoupon;
import com.glassvue.domain.coupon.repository.CouponRepository;
import com.glassvue.domain.coupon.repository.MemberCouponRepository;
import com.glassvue.domain.coupon.service.CouponService;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 「받기」를 <b>동시에</b> 눌러도 한 장만 나가는가 — G-8 의 본체.
 *
 * <p>🔴 <b>이 테스트만 롤백을 못 쓴다.</b> 경합은 «커밋된 것을 서로 못 보는 두 트랜잭션» 사이에서
 * 일어나므로, 한 트랜잭션 안에서 두 번 부르는 방식으로는 <b>재현 자체가 안 된다</b> —
 * 그렇게 쓰면 통과해도 아무것도 증명하지 못한다({@code EventCouponIntegrationTest} 의 «두 번 누르기»
 * 는 앱 가드를 보는 것이고, 여기서 보는 것은 <b>앱 가드가 뚫린 뒤의 DB</b>다).
 *
 * <p>⚠ 그래서 <b>실제 행이 커밋된다</b>. 만든 것은 {@link AfterEach} 에서 되돌린다 —
 * 우리가 넣은 행이라 지울 수 있다(2026-08-12 §9 와 같은 판단). 이름에 {@code ZZ} 를 붙여
 * 혹시 남더라도 세는 쿼리에 잡히게 한다(WA §3-1).
 *
 * <p>⚠ 앱 가드({@code exists})가 먼저 걸러 버리면 유니크 인덱스가 <b>일을 안 하고도 통과</b>한다.
 * 그래서 두 스레드를 래치로 같은 순간에 풀어 «둘 다 없다를 읽는» 창을 최대한 연다.
 * <b>그 창이 안 열려도 결과(정확히 한 장)는 같아야 한다</b> — 이 단언은 어느 층이 막았든 참이다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
class EventCouponConcurrencyTest {

    @Autowired CouponService couponService;
    @Autowired CouponRepository couponRepository;
    @Autowired MemberCouponRepository memberCouponRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TransactionTemplate transactionTemplate;

    private UUID memberId;
    private UUID couponId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(Member.builder()
                .loginId("evrace_" + UUID.randomUUID().toString().substring(0, 8))
                .password(passwordEncoder.encode("password123"))
                .nickname("ZZ경합" + UUID.randomUUID().toString().substring(0, 4))
                .role(Role.USER).build()).getId();

        Instant now = Instant.now();
        couponId = couponRepository.save(Coupon.builder()
                .name("ZZ동시받기 " + UUID.randomUUID().toString().substring(0, 8))
                .discountType(DiscountType.FIXED).discountValue(1000).minOrderAmount(0)
                .validFrom(now.minus(1, ChronoUnit.HOURS))
                .issueUntil(now.plus(1, ChronoUnit.HOURS))
                .validUntil(now.plus(30, ChronoUnit.DAYS))
                .build()).getId();
    }

    /**
     * ⚠ 순서가 있다 — 발급분을 먼저 지워야 쿠폰 정의를 지울 수 있다(참조가 남으면 FK 가 막는다).
     *
     * <p>🔴 <b>{@code TransactionTemplate} 이 필요하다.</b> 이 클래스는 경합을 재현하려고
     * {@code @Transactional} 을 뺐는데, 그러면 파생 삭제 쿼리({@code deleteByMemberId})가
     * {@code TransactionRequiredException} 으로 죽는다. ⚠ 실제로 여기서 한 번 터졌고,
     * <b>정리가 실패한 채 커밋된 이벤트 쿠폰이 남아 같은 클래스의 다른 테스트 여섯 개가
     * 그걸 보고 깨졌다</b>(2026-08-13) — 정리 실패는 자기 자신만 망가뜨리지 않는다.
     */
    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            memberCouponRepository.deleteByMemberId(memberId);
            couponRepository.deleteById(couponId);
            memberRepository.deleteById(memberId);
        });
    }

    @Test
    @DisplayName("두 요청이 같은 순간 「받기」를 눌러도 발급은 정확히 한 장이고, 진 쪽은 409 를 받는다")
    void concurrentClaimIssuesExactlyOne() throws Exception {
        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger issued = new AtomicInteger();
        AtomicInteger alreadyIssued = new AtomicInteger();

        Callable<Void> claim = () -> {
            start.await();
            try {
                couponService.claimEventCoupon(memberId);
                issued.incrementAndGet();
            } catch (BusinessException e) {
                // 진 쪽에 나가는 답이 «에러» 가 아니라 «이미 받음» 이어야 화면이 버튼을 확정할 수 있다.
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_ALREADY_ISSUED);
                alreadyIssued.incrementAndGet();
            }
            return null;
        };

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Void>> futures = List.of(pool.submit(claim), pool.submit(claim));
            start.countDown();
            for (Future<Void> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(issued.get()).isEqualTo(1);
        assertThat(alreadyIssued.get()).isEqualTo(1);
        assertThat(memberCouponRepository.findUnusedByMember(memberId)).hasSize(1);
    }

    /**
     * 🔴 <b>유니크 인덱스가 실제로 서 있는가</b> — 앱 가드를 <b>건너뛰고</b> 직접 밟는다.
     *
     * <p>⚠ 위 테스트는 «한 장» 을 확인하지만 <b>어느 층이 막았는지는 말해 주지 않는다.</b>
     * 두 스레드가 실제로는 어긋나 돌아 뒤엣놈이 앞엣놈의 커밋을 읽었다면, 인덱스가 없어도
     * 똑같이 통과한다 — «막았는데 0인지, 안 밟아서 0인지»(WA §3-3)가 갈리지 않는다.
     * 그래서 여기서는 서비스를 거치지 않고 리포지토리로 같은 쌍을 두 번 넣는다.
     *
     * <p>⚠ 이것이 <b>V49 인덱스의 유일한 직접 증거</b>다. 인덱스가 사라지면(마이그레이션 누락·수동 DROP)
     * 위 테스트는 계속 통과하고 <b>이 테스트만 깨진다.</b>
     */
    @Test
    @DisplayName("앱 가드를 건너뛰고 같은 쌍을 두 번 넣으면 DB 가 막는다 — ux_member_coupon_once")
    void uniqueIndexRejectsDuplicatePair() {
        Coupon coupon = couponRepository.findById(couponId).orElseThrow();

        transactionTemplate.executeWithoutResult(status ->
                memberCouponRepository.saveAndFlush(MemberCoupon.issue(memberId, coupon)));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                memberCouponRepository.saveAndFlush(MemberCoupon.issue(memberId, coupon))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
