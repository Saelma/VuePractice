package com.glassvue.domain.notification.entity;

/**
 * 알림 종류 (2026-07-24). 알림 row 의 분류이자 <b>켜기/끄기 설정의 단위</b>다.
 *
 * <p>주문 세부 이벤트(생성·배송완료·취소)를 {@code ORDER} 하나로 묶은 이유: 설정에서 사용자가
 * "주문 알림"을 통째로 켜고 끄는 게 자연스럽고, 이벤트마다 토글을 두면 설정이 번잡해진다.
 * 제목·내용은 이벤트마다 다르게 담고, 분류만 굵게 잡는다. 재입고(B-9)·문의 답변(B-15) 등은 여기 값을 추가해 확장한다.
 *
 * <p>⚠ <b>값을 늘려도 마이그레이션이 필요 없다</b> — {@code notification.type}·
 * {@code member_notification_pref.type} 은 {@code VARCHAR2(30)} 이고 <b>CHECK 제약이 없다</b>(V26 실측).
 * {@code orders.status}(2026-07-16)·{@code point_history.type} 처럼 제약이 걸린 컬럼이면 값 추가마다
 * ALTER 마이그레이션이 따라오는데, 여기는 그 함정이 없다. 설정 화면도 서버가
 * {@code NotificationType.values()} 를 통째로 내려주므로(NotificationQueryService.settings)
 * <b>토글이 저절로 하나 늘어난다</b> — 프론트 변경 불필요.
 */
public enum NotificationType {

    ORDER("주문 알림"),   // 주문 생성·배송완료·취소 (구매자 대상)
    STOCK("재고 알림"),   // 재고 부족 (관리자 대상)
    RESTOCK("재입고 알림"), // 품절 상품 재입고 (신청한 구매자 대상, B-9)
    INQUIRY("문의 답변 알림"), // 내 상품 문의에 관리자 답변이 달림 (작성자 대상, B-15)

    /**
     * 반품 요청 알림 (2026-08-12, 08-11 이월) — <b>관리자 대상</b>.
     *
     * <p>🔴 <b>{@code ORDER} 로 보내지 않은 이유</b>: 토글은 «종류 × 회원» 단위라, 관리자가
     * 자기 주문 알림을 끄면 <b>업무 알림까지 함께 꺼진다.</b> «내 주문이 어떻게 됐나» 와
     * «처리할 일이 들어왔다» 는 <b>끄고 켜는 이유가 다르므로</b> 값을 나눈다.
     * ⚠ {@code ORDER} 주석이 «구매자 대상» 이라고 적혀 있는 것도 같은 이야기다.
     *
     * <p>⚠ <b>구매자에게 가는 반품 승인·거절은 여전히 {@code ORDER} 다.</b> 같은 «반품» 이라도
     * 받는 사람이 다르면 다른 알림이다 — 그래서 값 이름이 {@code RETURN} 이 아니라
     * {@code RETURN_REQUEST} 다(«반품» 이 두 종류에 걸치면 설정 화면에서 무엇이 꺼지는지 알 수 없다).
     */
    RETURN_REQUEST("반품 요청 알림"),

    /**
     * 마케팅 알림 (2026-08-03, B-21 후속) — 관리자가 <b>직접 작성해서</b> 보내는 첫 알림이다.
     * 나머지 넷은 전부 이벤트에서 자동 생성된다.
     *
     * <p>⚠ 이 토글은 <b>수신 거부(선호)</b>이지 <b>동의 철회</b>가 아니다. 둘은 다른 것을 뜻한다:
     * <ul>
     *   <li>{@code member.marketing_agreed_at} — <b>동의했나</b>(일어난 사건, 근거로 쓴다)</li>
     *   <li>여기 토글 — <b>지금 받고 싶나</b>(현재 선호, 자유롭게 바뀐다)</li>
     * </ul>
     * 그래서 <b>둘 다 봐야</b> 보낸다: 동의 안 했으면 애초에 대상이 아니고, 동의했어도 토글을 껐으면
     * 만들지 않는다({@code NotificationCommandService.create} 가 이미 그렇게 동작한다).
     * ⚠ 합쳐서 한 값으로 두지 않은 이유는 <b>토글을 끌 때 동의 기록이 지워지면 안 되기 때문</b>이다 —
     * 그러면 "이 사람이 언제 동의했었나"에 영영 답할 수 없다.
     */
    MARKETING("마케팅 알림");

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
