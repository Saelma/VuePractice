package com.glassvue.domain.review.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 상품 리뷰. 상품(catalog)에는 느슨한 UUID 참조(product_id)로만 연결한다(도메인 경계 유지).
 * 구매 인증은 작성 시점에 order 도메인 공개 서비스로 검증한다(엔티티엔 저장 안 함).
 */
@Entity
@Getter
@Table(name = "review")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "product_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID productId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "author_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false, length = 50)
    private String author; // 표시용 닉네임(작성 시점 스냅샷)

    @Column(nullable = false)
    private int rating; // 별점 1~5

    @Lob
    @Column(nullable = false)
    private String content;

    @Builder
    private Review(UUID productId, UUID authorId, String author, int rating, String content) {
        this.productId = productId;
        this.authorId = authorId;
        this.author = author;
        this.rating = rating;
        this.content = content;
    }

    public boolean isOwnedBy(UUID memberId) {
        return authorId.equals(memberId);
    }

    public void update(int rating, String content) {
        this.rating = rating;
        this.content = content;
    }
}
