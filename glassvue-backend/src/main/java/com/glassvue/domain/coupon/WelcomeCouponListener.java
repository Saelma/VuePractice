package com.glassvue.domain.coupon;

import com.glassvue.domain.member.event.MemberSignedUpEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 가입 이벤트 리스너(어댑터). {@code OrderEventListener}·{@code InquiryEventListener} 와 같은 규약 —
 * <b>수신·위임만, 로직 없음</b>.
 *
 * <ul>
 *   <li>{@code AFTER_COMMIT}: 가입이 <b>커밋된 뒤에만</b> 발급한다. 롤백된 가입에 쿠폰이 남으면
 *       주인 없는 발급분이 된다.</li>
 *   <li>{@code @Async}: 발급을 가입 요청 스레드에서 분리한다 — 쿠폰 발급이 느리거나 실패해도
 *       <b>가입 응답이 늦어지거나 실패하지 않는다</b>({@link WelcomeCouponHandler} 주석).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class WelcomeCouponListener {

    private final WelcomeCouponHandler welcomeCouponHandler;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberSignedUp(MemberSignedUpEvent event) {
        welcomeCouponHandler.handle(event);
    }
}
