package com.glassvue.domain.coupon;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.glassvue.domain.coupon.dto.CouponResponse;
import com.glassvue.domain.coupon.entity.DiscountType;
import com.glassvue.domain.coupon.service.CouponService;
import com.glassvue.domain.member.event.MemberSignedUpEvent;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 가입 쿠폰 자동 발급 핸들러 (G-2, 2026-07-31 / V36 으로 지정 방식 변경).
 *
 * <p>세 갈래를 못박는다 — 셋 다 <b>가입은 이미 끝난 뒤</b>에 벌어지는 일이라, 틀려도
 * 사용자에게 보이는 건 "쿠폰이 안 왔다" 뿐이고 서버는 조용하다.
 *
 * <ol>
 *   <li><b>지정된 쿠폰이 없으면 아무 일도 안 한다</b>(기능 꺼짐이 기본 상태다 — 경고도 아니다).</li>
 *   <li>지정돼 있으면 <b>그 쿠폰을 그 회원에게</b> 발급한다.</li>
 *   <li>⚠ <b>발급이 실패해도 예외를 밖으로 내보내지 않는다</b> — 되돌릴 가입이 이미 없다.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class WelcomeCouponHandlerTest {

    @Mock CouponService couponService;
    @InjectMocks WelcomeCouponHandler handler;

    private final UUID memberId = UUID.randomUUID();
    private final UUID couponId = UUID.randomUUID();

    private MemberSignedUpEvent event() {
        return new MemberSignedUpEvent(memberId, "hong");
    }

    private CouponResponse welcome() {
        return new CouponResponse(couponId, "가입 축하 5천원", DiscountType.FIXED, 5_000L, 10_000L, null,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(60), true, Instant.now());
    }

    @Test
    @DisplayName("지정된 가입 쿠폰을 **가입한 그 회원**에게 발급한다")
    void issuesDesignatedCoupon() {
        given(couponService.welcomeCoupon()).willReturn(Optional.of(welcome()));

        handler.handle(event());

        verify(couponService).issue(couponId, memberId);
    }

    @Test
    @DisplayName("지정된 쿠폰이 없으면 발급하지 않는다 — 기능 꺼짐이 기본 상태다")
    void noDesignationNoIssue() {
        given(couponService.welcomeCoupon()).willReturn(Optional.empty());

        handler.handle(event());

        verify(couponService, never()).issue(any(), any());
    }

    @Test
    @DisplayName("⚠ 발급 도중 실패해도 **예외가 밖으로 나가지 않는다** — 가입은 이미 커밋됐다")
    void failureDoesNotBlowUp() {
        given(couponService.welcomeCoupon()).willReturn(Optional.of(welcome()));
        willThrow(new BusinessException(ErrorCode.COUPON_NOT_FOUND))
                .given(couponService).issue(couponId, memberId);

        // 여기서 예외가 새면 @Async 스레드에서 스택트레이스만 남고 아무도 안 본다.
        assertThatCode(() -> handler.handle(event())).doesNotThrowAnyException();
    }
}
