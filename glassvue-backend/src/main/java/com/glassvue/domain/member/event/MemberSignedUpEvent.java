package com.glassvue.domain.member.event;

import com.glassvue.global.messaging.DomainEvent;
import java.util.UUID;

/**
 * 새 회원이 가입했음을 알리는 도메인 이벤트 (2026-07-31, G-2).
 *
 * <p>가입 후처리를 <b>member 가 직접 하지 않기 위해</b> 있다. 지금 구독자는 coupon 도메인 하나
 * (가입 쿠폰 자동 발급)지만, 앞으로 붙을 것들(가입 축하 알림·마케팅 동의 등)도 여기 걸린다 —
 * 그때마다 {@code AuthService.signup()} 을 고치지 않는다.
 *
 * <p>⚠ <b>{@link MemberWithdrawnEvent} 와 처리 방식이 반대다.</b> 탈퇴 이벤트는 기본
 * {@code @EventListener} 라 <b>발행측 트랜잭션에 합류</b>한다(정리에 실패하면 회원 삭제도 롤백돼야 하므로).
 * 가입 이벤트는 {@code @Async} + {@code AFTER_COMMIT} 이다 — <b>쿠폰 발급이 실패해도 가입은
 * 유효해야 하기 때문</b>이다. 쿠폰은 나중에 관리자가 다시 줄 수 있지만, 가입 실패는 사용자가 다시
 * 겪어야 하는 일이다.
 *
 * <p>같은 이유로 <b>적립금 계정 생성은 이 이벤트로 빼지 않았다</b> — 계정이 없으면 적립·사용이
 * 통째로 막히므로 가입 트랜잭션 안에서 동기로 끝낸다({@code AuthService.signup} 주석,
 * 배송완료 적립을 이벤트로 안 뺀 것과 같은 판단).
 *
 * @param memberId 가입한 회원 id
 * @param loginId  로그에서 사람이 알아보기 위한 값(개인정보가 아니라 식별자)
 */
public record MemberSignedUpEvent(UUID memberId, String loginId) implements DomainEvent {
}
