package com.glassvue.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.glassvue.domain.coupon.entity.Coupon;
import com.glassvue.domain.coupon.entity.DiscountType;
import com.glassvue.domain.coupon.entity.MemberCoupon;
import com.glassvue.domain.coupon.repository.CouponRepository;
import com.glassvue.domain.coupon.repository.MemberCouponRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 쿠폰 사용 처리 (H-7, 2026-07-31).
 *
 * <p><b>왜 이 파일이 이제야 생겼나</b>: H-5(JaCoCo) 실측에서 {@code CouponService.redeem} 이
 * <b>0%</b>, {@code MemberCoupon} 의 {@code isOwnedBy}·{@code isUsed}·{@code use} 도 <b>0%</b> 였다.
 * 쿠폰이 <b>실제로 쓰이는 경로 전체</b>가 한 번도 실행된 적이 없었다 —
 * {@code CouponFlowIntegrationTest} 는 <b>생성·발급·미리보기</b>까지만 보고,
 * {@code OrderServiceTest} 는 쿠폰 없는 주문만 만든다(H-2 의 {@code @Mock} 착시와 같은 모양).
 *
 * <p>{@code redeem} 은 <b>order 도메인의 진입점</b>이라 주문 트랜잭션 안에서 돌고,
 * 여기서 틀리면 <b>남의 쿠폰으로 할인이 먹거나 한 장이 두 번 쓰인다</b>. 가드 넷을 각각 못박는다.
 *
 * <p>엔티티({@link Coupon}·{@link MemberCoupon})는 <b>목으로 대체하지 않고 진짜를 쓴다</b> —
 * 검증 대상이 "가드가 실제로 막는가" 라서, 상태를 흉내 내면 아무것도 확인하지 못한다.
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock CouponRepository couponRepository;
    @Mock MemberCouponRepository memberCouponRepository;
    @InjectMocks CouponService couponService;

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID MC_ID = UUID.randomUUID();

    private static final Instant YEAR_AGO = Instant.now().minus(365, ChronoUnit.DAYS);
    private static final Instant NEXT_YEAR = Instant.now().plus(365, ChronoUnit.DAYS);

    /** 정액 5,000원 · 최소주문 30,000원 · 지금 유효. 필요한 축만 인자로 흔든다. */
    private Coupon coupon(long minOrder, Instant from, Instant until) {
        return Coupon.builder()
                .name("ZZ 5천원").discountType(DiscountType.FIXED).discountValue(5_000L)
                .minOrderAmount(minOrder).validFrom(from).validUntil(until).build();
    }

    private MemberCoupon issuedTo(UUID memberId, Coupon coupon) {
        MemberCoupon mc = MemberCoupon.issue(memberId, coupon);
        given(memberCouponRepository.findById(MC_ID)).willReturn(Optional.of(mc));
        return mc;
    }

    private void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    // ── 성공 경로 ─────────────────────────────────────────────

    @Test
    @DisplayName("사용 → 할인액을 돌려주고 **그 자리에서 사용 처리**한다")
    void redeemMarksUsedAndReturnsDiscount() {
        MemberCoupon mc = issuedTo(OWNER, coupon(30_000L, YEAR_AGO, NEXT_YEAR));

        long discount = couponService.redeem(MC_ID, OWNER, 30_000L);

        assertThat(discount).isEqualTo(5_000L);
        // 검증과 사용처리가 갈리면 그 사이에 같은 쿠폰이 두 번 쓰인다(서비스 javadoc) — 한 호출에서 끝나야 한다.
        assertThat(mc.isUsed()).isTrue();
    }

    // ── 가드 넷 (전부 미실행이던 자리) ────────────────────────

    @Test
    @DisplayName("⚠ 남의 쿠폰은 **\"없는 것\"** 으로 답한다 — 존재 여부를 알려주지 않는다")
    void strangersCouponLooksMissing() {
        MemberCoupon mc = issuedTo(OWNER, coupon(0L, YEAR_AGO, NEXT_YEAR));

        // ALREADY_USED·EXPIRED 로 답하면 "그 쿠폰이 있긴 하다"가 새어 나간다.
        assertErrorCode(() -> couponService.redeem(MC_ID, STRANGER, 50_000L), ErrorCode.COUPON_NOT_FOUND);
        assertThat(mc.isUsed()).isFalse();   // 남이 남의 쿠폰을 소모시키지도 못한다
    }

    @Test
    @DisplayName("⚠ 이미 쓴 쿠폰은 두 번 안 먹는다 — 한 장으로 두 번 할인받는 자리")
    void alreadyUsedIsRejected() {
        MemberCoupon mc = issuedTo(OWNER, coupon(0L, YEAR_AGO, NEXT_YEAR));
        mc.use();

        assertErrorCode(() -> couponService.redeem(MC_ID, OWNER, 50_000L), ErrorCode.COUPON_ALREADY_USED);
    }

    @Test
    @DisplayName("기간이 지난 쿠폰은 거부한다")
    void expiredIsRejected() {
        MemberCoupon mc = issuedTo(OWNER, coupon(0L, YEAR_AGO, Instant.now().minus(1, ChronoUnit.DAYS)));

        assertErrorCode(() -> couponService.redeem(MC_ID, OWNER, 50_000L), ErrorCode.COUPON_EXPIRED);
        assertThat(mc.isUsed()).isFalse();   // ⚠ 거부된 쿠폰이 소모되면 사용자는 쓰지도 못하고 잃는다
    }

    @Test
    @DisplayName("최소 주문금액을 못 채우면 거부하고 **쿠폰은 그대로 남는다**")
    void minOrderNotMet() {
        MemberCoupon mc = issuedTo(OWNER, coupon(30_000L, YEAR_AGO, NEXT_YEAR));

        assertErrorCode(() -> couponService.redeem(MC_ID, OWNER, 29_999L), ErrorCode.COUPON_MIN_ORDER_NOT_MET);
        assertThat(mc.isUsed()).isFalse();
    }

    @Test
    @DisplayName("없는 쿠폰 id 도 COUPON_NOT_FOUND")
    void missingCoupon() {
        given(memberCouponRepository.findById(any())).willReturn(Optional.empty());

        assertErrorCode(() -> couponService.redeem(MC_ID, OWNER, 50_000L), ErrorCode.COUPON_NOT_FOUND);
    }

    // ── 주문 스냅샷용 이름 ────────────────────────────────────

    @Test
    @DisplayName("주문 스냅샷용 쿠폰명 — 없으면 null(주문은 계속돼야 한다)")
    void nameOf() {
        issuedTo(OWNER, coupon(0L, YEAR_AGO, NEXT_YEAR));
        assertThat(couponService.nameOf(MC_ID)).isEqualTo("ZZ 5천원");

        UUID unknown = UUID.randomUUID();
        given(memberCouponRepository.findById(unknown)).willReturn(Optional.empty());
        // ⚠ 여기서 예외를 던지면 "이름을 못 찾았다"는 이유로 **주문 전체가 실패**한다.
        assertThat(couponService.nameOf(unknown)).isNull();
    }
}
