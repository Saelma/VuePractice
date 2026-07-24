package com.glassvue.domain.wishlist.entity;

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
 * 찜(위시리스트) 한 줄 — "이 회원이 이 상품을 골라 뒀다".
 *
 * <p>수량도 옵션도 없다. 장바구니는 "지금 사려는 것"이라 수량이 필요하지만 찜은 <b>표시</b>일 뿐이라
 * 회원·상품 한 쌍이면 끝난다. 나중에 "찜한 순서"를 보여줘야 하므로 {@code createdAt}은 쓴다.
 *
 * <p>{@code memberId}·{@code productId} 둘 다 <b>FK 없는 느슨한 UUID</b>다(도메인 경계).
 * 그래서 <b>상품이 삭제돼도 이 행은 남는다</b> — 목록을 만들 때 걸러낸다
 * ({@code WishlistQueryService} 참조. 장바구니가 삭제된 상품을 정리하는 것과 같은 처리).
 */
@Entity
@Getter
@Table(name = "wishlist")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wishlist extends BaseTimeEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "member_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID memberId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "product_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID productId;

    private Wishlist(UUID memberId, UUID productId) {
        this.memberId = memberId;
        this.productId = productId;
    }

    public static Wishlist of(UUID memberId, UUID productId) {
        return new Wishlist(memberId, productId);
    }
}
