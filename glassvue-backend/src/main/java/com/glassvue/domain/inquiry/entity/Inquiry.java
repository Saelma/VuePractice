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
 * 문의(Q&A). 답변은 관리자 전용.
 * secret(비밀글)이면 작성자·관리자만 본문/답변을 볼 수 있다(마스킹은 응답 DTO에서).
 *
 * <p><b>두 종류가 한 테이블에 산다</b>(2026-08-07, G-3 2단계):
 * <ul>
 *   <li><b>상품 문의</b> — {@code productId} 가 있고 {@code type == PRODUCT}. 상품 상세에서 쓴다.
 *   <li><b>일반 고객센터 문의</b> — {@code productId} 가 <b>null</b> 이고 type 은 나머지 중 하나.
 *       {@code /support} 에서 쓴다.
 * </ul>
 * 나누지 않은 이유: 관리자 답변 API·답변 알림(B-15)·관리자 목록(G-3 1단계)이 <b>전부 그대로
 * 재사용된다.</b> 별도 테이블로 만들면 관리자가 <b>두 목록을 번갈아 봐야</b> 한다.
 */
@Entity
@Getter
@Table(name = "inquiry")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseTimeEntity {

    /**
     * 상품 문의면 상품 id(느슨한 UUID 참조, FK 아님), 일반 문의면 <b>null</b>.
     *
     * <p>⚠ nullable 로 열렸지만 {@code updatable = false} 는 <b>그대로다</b> — 일반 문의를 나중에
     * 상품에 갖다 붙이는 경로는 만들지 않는다(2026-08-07 결정). 붙일 수 있게 하면 "이 문의가 어느
     * 상품 것인가" 가 시간에 따라 변해 이미 나간 답변·알림 링크가 어긋난다.
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "product_id", columnDefinition = "RAW(16)", updatable = false)
    private UUID productId;

    /**
     * 문의 유형. PRODUCT 는 <b>경로가 정하고</b> 나머지는 사용자가 고른다.
     * ⚠ {@code productId} 와 짝이다 — 아래 생성자와 DB 제약 {@code ck_inquiry_product_pair} 가 함께 지킨다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "inquiry_type", nullable = false, length = 20, updatable = false)
    private InquiryType type;

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

    /**
     * ⚠ <b>빌더를 유지한 이유</b>: 정적 팩터리 두 개({@code forProduct}/{@code general})가 «구조로
     * 막는» 모양에 더 가깝지만, 여기는 {@code author}·{@code title}·{@code content} 가 <b>붙어 있는
     * 세 String</b> 이라 위치 인자로 받으면 <b>순서를 바꿔 넣어도 컴파일된다.</b> 그 위험이
     * 유형·상품 짝을 못 맞추는 위험보다 크다 — 후자는 아래 한 줄로 잡히지만 전자는 안 잡힌다.
     *
     * <p>대신 <b>생성 시점에</b> 짝을 검증한다. DB 제약({@code ck_inquiry_product_pair})이 최종
     * 방어선이지만, 거기까지 가면 예외가 <b>flush 시점</b>에 터져 어느 코드가 만들었는지 알기 어렵다.
     */
    @Builder
    private Inquiry(UUID productId, InquiryType type, UUID authorId, String author, String title,
                    String content, boolean secret, UUID imageGroupId) {
        if (type == null) {
            throw new IllegalArgumentException("문의 유형은 필수입니다");
        }
        // 같은 사실의 두 면이라 어긋나면 «멀쩡히 도는데 틀린» 행이 된다:
        // type=PRODUCT 인데 productId 가 없으면 관리자 목록의 상품명이 영원히 «—» 이고,
        // 반대면 그 일반 문의가 상품 문의 목록에 섞여 뜬다.
        if (type.requiresProduct() != (productId != null)) {
            throw new IllegalArgumentException(
                    "문의 유형과 상품이 어긋납니다: type=" + type + ", productId=" + productId);
        }
        this.productId = productId;
        this.type = type;
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
