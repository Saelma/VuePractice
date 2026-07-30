package com.glassvue.domain.wishlist;

import com.glassvue.domain.member.event.MemberWithdrawnEvent;
import com.glassvue.domain.wishlist.service.command.WishlistCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 회원이 사라지면 찜을 지운다 — member 도메인의 {@link MemberWithdrawnEvent}를 받아 잇는 <b>어댑터</b>다(위임만 한다).
 *
 * <p>기본 {@code @EventListener} 라 발행측 트랜잭션에 합류한다 — 정리가 실패하면 회원 삭제도 롤백된다.
 * 로직은 두지 않는다(진짜 주체는 {@link WishlistCommandService }).
 */
@Component
@RequiredArgsConstructor
public class WishlistMemberWithdrawnListener {

    private final WishlistCommandService wishlistCommandService;

    @EventListener
    public void on(MemberWithdrawnEvent event) {
        wishlistCommandService.deleteAllForMember(event.memberId());
    }
}
