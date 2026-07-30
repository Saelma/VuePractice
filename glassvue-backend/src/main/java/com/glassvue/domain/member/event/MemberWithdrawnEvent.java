package com.glassvue.domain.member.event;

import java.util.UUID;

/**
 * 회원이 사라졌음을 알리는 도메인 이벤트 — <b>회원 데이터 정리의 공개 계약</b>이다(F-1).
 *
 * <p>이 프로젝트는 도메인 간 FK 를 두지 않는다(느슨한 UUID). 그래서 회원 행을 지워도
 * <b>다른 도메인의 데이터는 그대로 남아 주인 없는 행이 된다</b> — 실측(2026-07-29) 당시 고아
 * {@code point_account} 가 7행, 2026-07-30 검증 후 9행이었다. member 가 남의 테이블을 직접 지울 수는
 * 없으므로(도메인 경계), <b>각 도메인이 이 이벤트를 받아 자기 것만 지운다.</b>
 *
 * <p><b>지우는 쪽 / 남기는 쪽</b> — 판단 근거를 여기 남긴다(코드가 흩어져 있어 한눈에 안 보인다):
 *
 * <ul>
 *   <li><b>지운다</b>: 배송지(개인정보) · 찜 · 적립금 계정·이력 · 보유 쿠폰 · 알림·알림설정 ·
 *       재입고 구독 · <b>문의</b>(본인↔관리자 대화라 내용에 개인정보가 들어갈 수 있고 비밀글도 있다).
 *   <li><b>남긴다</b>: <b>주문</b>(매출 집계의 근거이고 구매자명이 이미 스냅샷이다) ·
 *       <b>리뷰</b>(다른 고객의 구매 판단 근거 + {@code product.avg_rating}·{@code review_count} 집계의 근거) ·
 *       <b>공지</b>(관리자가 쓴 콘텐츠 — 작성자 계정이 사라져도 글은 남아야 한다).
 * </ul>
 *
 * <p>남길 수 있는 이유는 그 셋 모두 <b>작성 시점 닉네임을 스냅샷</b>으로 들고 있어서다
 * ({@code orders.buyer_nickname}, {@code review.author}, {@code notice.author}) — 회원이 없어도 표시가 깨지지 않는다.
 *
 * <p>기본 {@code @EventListener} 라 <b>발행측 트랜잭션에 합류</b>한다(감사 로그와 같은 판단):
 * 정리 중 하나라도 실패하면 <b>회원 삭제 자체가 롤백</b>되어, "회원은 사라졌는데 데이터는 남은" 어긋난
 * 상태가 생기지 않는다.
 *
 * <p>발행 지점은 <b>둘</b>이다 — 본인 탈퇴({@code MemberService.withdraw})와 관리자 강제 삭제
 * ({@code MemberAdminCommandService.delete}, B-24). 정리 로직을 공유하려고 이벤트로 묶었다.
 *
 * @param memberId 사라진 회원 id
 */
public record MemberWithdrawnEvent(UUID memberId) {
}
