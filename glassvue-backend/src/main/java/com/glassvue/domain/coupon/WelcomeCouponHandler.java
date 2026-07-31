package com.glassvue.domain.coupon;

import com.glassvue.domain.coupon.dto.CouponResponse;
import com.glassvue.domain.coupon.service.CouponService;
import com.glassvue.domain.member.event.MemberSignedUpEvent;
import com.glassvue.global.exception.BusinessException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 쿠폰 자동 발급 — 이벤트에 반응하는 <b>"진짜 주체"</b> (2026-07-31, G-2).
 *
 * <p>그전엔 쿠폰이 <b>관리자가 특정 회원에게 지정 발급</b>하는 경로뿐이라 <b>자동 발급이 아예 없었다</b>.
 * 그래서 홈 혜택 스트립에 *"가입하면 쿠폰"* 을 못 썼다 — 문구가 아니라 <b>기능이 없어서</b>였다
 * (2026-07-29 핸드오프 §7-2, `HomeView` 주석).
 *
 * <p><b>어떤 쿠폰을 주나</b>: 관리자 화면에서 <b>「가입 쿠폰」으로 지정한</b> 쿠폰 하나(V36).
 * 지정된 게 없으면 기능이 꺼진다(기본 상태). 총량·경쟁이 없는 <b>전원 지급</b>이라 동시성 문제가 없다 —
 * 백로그 D 의 「선착순 발급」과 다른 자리다.
 *
 * <p>⚠ 처음엔 설정(.env)으로 가리켰는데 <b>바꿀 때마다 재시작</b>이 필요했고 무엇이 가입 쿠폰인지
 * 화면에서 안 보였다(사용자 지적). 쿠폰이 데이터니 지정도 데이터다.
 *
 * <p>⚠ <b>여기서 나는 예외는 밖으로 안 내보낸다.</b> 이 핸들러는 가입 커밋 <b>뒤</b>에 비동기로 도는데,
 * 설정된 쿠폰이 지워졌거나 id 가 틀렸다고 해서 되돌릴 가입이 이미 없다. 쿠폰은 관리자가 나중에
 * 다시 줄 수 있지만 <b>가입 실패는 사용자가 다시 겪어야 하는 일</b>이라, 실패는 로그로만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WelcomeCouponHandler {

    private final CouponService couponService;

    @Transactional
    public void handle(MemberSignedUpEvent event) {
        Optional<CouponResponse> welcome = couponService.welcomeCoupon();
        if (welcome.isEmpty()) {
            // 지정 안 함 = 기능 꺼짐. 경고가 아니다(기본 상태다).
            log.debug("[가입쿠폰] 지정된 쿠폰 없음 — member={} 건너뜀", event.memberId());
            return;
        }
        UUID couponId = welcome.get().id();
        try {
            UUID issued = couponService.issue(couponId, event.memberId());
            log.info("[가입쿠폰] member={}({}) 에게 발급 — memberCoupon={}",
                    event.memberId(), event.loginId(), issued);
        } catch (BusinessException e) {
            log.warn("[가입쿠폰] 발급 실패 — member={} coupon={} ({}). 가입은 정상 완료됐다.",
                    event.memberId(), couponId, e.getMessage());
        }
    }
}
