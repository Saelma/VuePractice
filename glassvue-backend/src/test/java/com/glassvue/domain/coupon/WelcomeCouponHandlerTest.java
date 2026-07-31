package com.glassvue.domain.coupon;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.glassvue.domain.coupon.config.CouponProperties;
import com.glassvue.domain.coupon.service.CouponService;
import com.glassvue.domain.member.event.MemberSignedUpEvent;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 가입 쿠폰 자동 발급 핸들러 (G-2, 2026-07-31).
 *
 * <p>세 갈래를 못박는다 — 셋 다 <b>가입은 이미 끝난 뒤</b>에 벌어지는 일이라, 틀려도
 * 사용자에게 보이는 건 "쿠폰이 안 왔다" 뿐이고 서버는 조용하다.
 *
 * <ol>
 *   <li><b>설정이 없으면 아무 일도 안 한다</b>(기능 꺼짐이 기본값이다 — 경고도 아니다).</li>
 *   <li>설정이 있으면 <b>그 쿠폰을 그 회원에게</b> 발급한다.</li>
 *   <li>⚠ <b>발급이 실패해도 예외를 밖으로 내보내지 않는다</b> — 되돌릴 가입이 이미 없다.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class WelcomeCouponHandlerTest {

    @Mock CouponService couponService;

    private final UUID memberId = UUID.randomUUID();
    private final UUID couponId = UUID.randomUUID();

    /** 핸들러는 설정값에 따라 동작이 갈리므로, 설정만 진짜 객체로 갈아 끼운다. */
    private WelcomeCouponHandler handler(String configured) {
        return new WelcomeCouponHandler(new CouponProperties(configured), couponService);
    }

    private MemberSignedUpEvent event() {
        return new MemberSignedUpEvent(memberId, "hong");
    }

    @Test
    @DisplayName("설정된 쿠폰을 **가입한 그 회원**에게 발급한다")
    void issuesConfiguredCoupon() {
        handler(couponId.toString()).handle(event());

        verify(couponService).issue(couponId, memberId);
    }

    @Test
    @DisplayName("설정이 비어 있으면 발급하지 않는다 — 기능 꺼짐이 기본값이다")
    void noConfigNoIssue() {
        handler("").handle(event());

        verifyNoInteractions(couponService);
    }

    @Test
    @DisplayName("설정이 null 이어도(키 자체가 없어도) 조용히 넘어간다")
    void nullConfigNoIssue() {
        handler(null).handle(event());

        verifyNoInteractions(couponService);
    }

    @Test
    @DisplayName("⚠ UUID 형식이 아닌 설정은 기동을 막지 않고 **발급만 건너뛴다**")
    void malformedConfigIsIgnored() {
        handler("not-a-uuid").handle(event());

        verify(couponService, never()).issue(any(), any());
    }

    @Test
    @DisplayName("⚠ 설정된 쿠폰이 지워졌어도 **예외가 밖으로 나가지 않는다** — 가입은 이미 커밋됐다")
    void missingCouponDoesNotBlowUp() {
        willThrow(new BusinessException(ErrorCode.COUPON_NOT_FOUND))
                .given(couponService).issue(couponId, memberId);

        // 여기서 예외가 새면 @Async 스레드에서 스택트레이스만 남고 아무도 안 본다.
        assertThatCode(() -> handler(couponId.toString()).handle(event())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("공백이 섞인 설정값도 받아들인다(.env 편집 실수)")
    void trimsConfiguredValue() {
        given(couponService.issue(couponId, memberId)).willReturn(UUID.randomUUID());

        handler("  " + couponId + "  ").handle(event());

        verify(couponService).issue(couponId, memberId);
    }
}
