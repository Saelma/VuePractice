package com.glassvue.domain.point.entity;

/** 적립금이 움직인 이유. DB CHECK({@code ck_point_history_type})와 값이 같아야 한다. */
public enum PointType {

    /** 배송완료 적립. */
    EARN,

    /** 주문에서 사용. */
    USE,

    /**
     * 관리자 수동 조정 — 지금은 화면도 API 도 없다.
     *
     * <p>미리 만들지 않는다는 원칙에 어긋나 보이지만, 이건 <b>enum 값</b>이라 나중에 추가하면
     * DB CHECK 제약 교체 마이그레이션이 따라온다(2026-07-16 에 {@code orders.status} 로 실제로 겪었다).
     * 값 하나 미리 넣는 비용보다 그쪽이 크다.
     */
    ADJUST
}
