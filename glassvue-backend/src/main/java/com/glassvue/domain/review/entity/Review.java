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
 *
 * <p>포토 리뷰는 Product와 동일하게 {@code image_group_id}(느슨한 UUID) 하나만 들고
 * image 도메인 공개 서비스로 다룬다 — ImageGroup이 설계한 재사용 구조를 그대로 따른다.
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

    /** 포토 리뷰 이미지 묶음. 이미지가 없으면 null(= 그룹을 만들지 않는다). */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "image_group_id", columnDefinition = "RAW(16)")
    private UUID imageGroupId;

    /**
     * 관리자 숨김 (2026-08-04, V41, 백로그 B-18) — <b>삭제가 아니다.</b>
     *
     * <p>되돌릴 수 있어야 해서 원문을 남긴다. 관리자가 잘못 판단할 수 있고, 그때 본문이 있어야 되돌린다
     * (감사 로그는 "누가 숨겼는지" 를 남기지만 지워진 본문은 복구하지 못한다).
     *
     * <p>⚠ <b>같은 값을 세 곳이 다르게 다룬다</b> — 헷갈리기 쉬운 자리다:
     * <ul>
     *   <li><b>목록 조회</b> — 빠진다(작성자 본인에게도).</li>
     *   <li><b>별점 집계</b> — 빠진다. 안 빼면 보이지도 않는 리뷰가 별점을 끌어내린다.</li>
     *   <li>🔴 <b>상품당 1회 제한</b> — <b>그대로 센다.</b> 빼면 숨기자마자 새 리뷰를 쓸 수 있어
     *       숨김이 무의미해진다.</li>
     * </ul>
     */
    @Column(nullable = false)
    private boolean hidden;

    @Builder
    private Review(UUID productId, UUID authorId, String author, int rating, String content, UUID imageGroupId) {
        this.productId = productId;
        this.authorId = authorId;
        this.author = author;
        this.rating = rating;
        this.content = content;
        this.imageGroupId = imageGroupId;
    }

    public boolean isOwnedBy(UUID memberId) {
        return authorId.equals(memberId);
    }

    public void update(int rating, String content, UUID imageGroupId) {
        this.rating = rating;
        this.content = content;
        this.imageGroupId = imageGroupId;
    }

    /**
     * 관리자 숨김·해제 (B-18). {@code hidden} 만 바꾼다 — 본문·별점은 그대로 둔다(되돌릴 수 있어야 한다).
     *
     * <p>이미 그 상태면 <b>아무 일도 하지 않는다</b>. 호출부가 그걸로 "집계를 다시 낼 필요가 있는지" 를
     * 판단하므로 반환값이 필요하다 — 안 바뀐 요청에 이벤트를 또 발행하면 캐시가 헛되이 비워진다.
     *
     * @return 실제로 바뀌었으면 {@code true}
     */
    public boolean setHidden(boolean hidden) {
        if (this.hidden == hidden) {
            return false;
        }
        this.hidden = hidden;
        return true;
    }
}
