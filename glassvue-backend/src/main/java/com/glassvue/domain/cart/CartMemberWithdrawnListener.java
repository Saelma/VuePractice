package com.glassvue.domain.cart;

import com.glassvue.domain.cart.service.CartService;
import com.glassvue.domain.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 회원이 사라지면 장바구니를 지운다 — member 도메인의 {@link MemberWithdrawnEvent} 를 받아 잇는
 * <b>어댑터</b>다(위임만 한다). 2026-08-11, 08-10 §16-4 6번.
 *
 * <p>🔴 <b>정리 목록에서 장바구니만 빠져 있었다.</b> 탈퇴 시 적립금·찜·쿠폰·알림·재입고·문의·배송지는
 * 전부 지워지는데 장바구니는 그대로 남았다. 실측(2026-08-11): Redis 의 {@code cart:*} 키 <b>96개가
 * 전부 주인 없는 것</b>이었다(회원 6명, 매칭되는 키 0개).
 *
 * <p>⚠ <b>왜 빠졌나</b>: 다른 일곱은 전부 DB 테이블이라 «회원 정리» 를 이야기할 때 자연히 떠오르는데,
 * 장바구니만 <b>Redis</b> 다({@link CartStore}, 키 {@code cart:{memberId}}). F-1 을 만들 때
 * 「지울 테이블」을 세었지 「지울 저장소」를 세지 않았다 — <b>목록의 단위가 틀리면 항목이 빠진다</b>
 * (오늘 §1-1·§7-3 에서 «세는 대상» 이 틀렸던 것과 같은 종류).
 *
 * <p>⚠ TTL 30일이 결국 지우므로 <b>영구 누수는 아니다.</b> 그래도 고치는 이유는 F-1 의 성격이
 * «언젠가 사라진다» 가 아니라 <b>«탈퇴하면 지운다»</b> 이기 때문이다 — 최대 30일 동안
 * «그 사람이 무엇을 담아 뒀는지» 가 서버에 남는 것은 다른 도메인의 처리와 어긋난다.
 *
 * <p>⚠ <b>트랜잭션 밖이다.</b> 기본 {@code @EventListener} 라 발행측 트랜잭션에 합류하지만,
 * Redis 삭제는 <b>롤백되지 않는다</b> — 회원 삭제가 뒤에서 실패하면 장바구니만 지워진 채로 남는다.
 * 그래도 이쪽이 낫다고 봤다: 반대(회원은 지워졌는데 장바구니가 남는 것)가 <b>개인정보가 남는</b> 쪽이고,
 * 장바구니는 <b>다시 담으면 되는</b> 값이다. 같은 판단이 {@code RefreshTokenStore}·
 * {@code TokenRevocationStore} 정리에도 이미 적용돼 있다({@code MemberService.purge} 의 첫 두 줄).
 */
@Component
@RequiredArgsConstructor
public class CartMemberWithdrawnListener {

    private final CartService cartService;

    @EventListener
    public void on(MemberWithdrawnEvent event) {
        cartService.clear(event.memberId());
    }
}
