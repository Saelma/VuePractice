package com.glassvue.domain.notification.entity;

/**
 * 알림 종류 (2026-07-24). 알림 row 의 분류이자 <b>켜기/끄기 설정의 단위</b>다.
 *
 * <p>주문 세부 이벤트(생성·배송완료·취소)를 {@code ORDER} 하나로 묶은 이유: 설정에서 사용자가
 * "주문 알림"을 통째로 켜고 끄는 게 자연스럽고, 이벤트마다 토글을 두면 설정이 번잡해진다.
 * 제목·내용은 이벤트마다 다르게 담고, 분류만 굵게 잡는다. 재입고(B-9) 등은 여기 값을 추가해 확장한다.
 */
public enum NotificationType {

    ORDER("주문 알림"),   // 주문 생성·배송완료·취소 (구매자 대상)
    STOCK("재고 알림");   // 재고 부족 (관리자 대상)

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
