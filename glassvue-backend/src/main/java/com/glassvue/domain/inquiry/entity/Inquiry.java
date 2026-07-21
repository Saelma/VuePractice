package com.glassvue.domain.inquiry.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 상품 문의(Q&A). 상품(catalog)에는 느슨한 UUID 참조(product_id)로만 연결한다.
 * 답변은 관리자 전용. secret(비밀글)이면 작성자·관리자만 본문/답변을 볼 수 있다(마스킹은 응답 DTO에서).
 */
@Entity
@Getter
@Table(name = "inquiry")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseTimeEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "product_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID productId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "author_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false, length = 50)
    private String author; // 표시용 닉네임(작성 시점 스냅샷)

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean secret; // 비밀글

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status;

    @Lob
    @Column
    private String answer; // 관리자 답변(없으면 null)

    @Column
    private Instant answeredAt;

    // 첨부 이미지 그룹(느슨한 UUID 참조, 없으면 null). Review와 동일하게 ImageService 공개 API로만 다룬다.
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "image_group_id", columnDefinition = "RAW(16)")
    private UUID imageGroupId;

    @Builder
    private Inquiry(UUID productId, UUID authorId, String author, String title, String content,
                    boolean secret, UUID imageGroupId) {
        this.productId = productId;
        this.authorId = authorId;
        this.author = author;
        this.title = title;
        this.content = content;
        this.secret = secret;
        this.imageGroupId = imageGroupId;
        this.status = InquiryStatus.WAITING;
    }

    public boolean isOwnedBy(UUID memberId) {
        return authorId.equals(memberId);
    }

    public boolean isAnswered() {
        return status == InquiryStatus.ANSWERED;
    }

    public void update(String title, String content, boolean secret, UUID imageGroupId) {
        this.title = title;
        this.content = content;
        this.secret = secret;
        this.imageGroupId = imageGroupId;
    }

    /** 관리자 답변 등록/수정. 상태를 ANSWERED로 전환한다. */
    public void answer(String answer) {
        this.answer = answer;
        this.status = InquiryStatus.ANSWERED;
        this.answeredAt = Instant.now();
    }
}
