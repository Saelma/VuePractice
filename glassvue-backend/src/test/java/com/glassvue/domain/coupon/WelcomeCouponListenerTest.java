package com.glassvue.domain.coupon;

import static org.mockito.Mockito.verify;

import com.glassvue.domain.member.event.MemberSignedUpEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 3층 컨벤션 고정 — 리스너(어댑터)는 로직 없이 Handler 에 위임만 한다(G-2). */
@ExtendWith(MockitoExtension.class)
class WelcomeCouponListenerTest {

    @Mock WelcomeCouponHandler welcomeCouponHandler;
    @InjectMocks WelcomeCouponListener listener;

    @Test
    @DisplayName("리스너는 로직 없이 Handler에 위임만 한다")
    void delegatesToHandler() {
        MemberSignedUpEvent event = new MemberSignedUpEvent(UUID.randomUUID(), "hong");

        listener.onMemberSignedUp(event);

        verify(welcomeCouponHandler).handle(event);
    }
}
