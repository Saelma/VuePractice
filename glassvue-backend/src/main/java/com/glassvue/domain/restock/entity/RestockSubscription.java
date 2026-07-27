package com.glassvue.domain.restock.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 재입고 알림 신청 한 줄 — "이 회원이 이 상품이 다시 들어오면 알려달라고 했다"(B-9).
 *
 * <p>단위가 <b>상품</b>인 이유는 {@code StockReplenishedEvent} 와 같다 — 관리자 상품 편집이 옵션을
 * delete + 재삽입하며 variant.id 가 매번 바뀌어, 옵션 id 로 걸면 구독이 고아가 된다. 그래서 위시리스트와
 * 똑같이 (member, product) 한 쌍으로 잡는다.
 *
 * <p>{@code memberId}·{@code productId} 둘 다 <b>FK 없는 느슨한 UUID</b>다(도메인 경계). 상품이 삭제돼도
 * 이 행은 남을 수 있으나, 재입고 이벤트는 살아 있는 상품에서만 나므로 죽은 구독은 그냥 발화되지 않는다
 * (알림 발송 후에는 해당 상품 구독을 통째로 지워 재알림을 막는다 — {@code RestockNotificationHandler}).
 */
@Entity
@Getter
@Table(name = "restock_subscription")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestockSubscription extends BaseTimeEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "member_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID memberId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "product_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID productId;

    private RestockSubscription(UUID memberId, UUID productId) {
        this.memberId = memberId;
        this.productId = productId;
    }

    public static RestockSubscription of(UUID memberId, UUID productId) {
        return new RestockSubscription(memberId, productId);
    }
}
