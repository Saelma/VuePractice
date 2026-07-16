package com.glassvue.domain.inquiry.dto;

import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryStatus;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.global.security.AuthUser;
import java.time.Instant;
import java.util.UUID;

/**
 * 문의 응답. 비밀글은 작성자·관리자가 아니면 본문/답변을 마스킹한다(masked=true).
 */
public record InquiryResponse(
        UUID id,
        UUID productId,
        UUID authorId,
        String author,
        String title,
        String content,
        boolean secret,
        InquiryStatus status,
        String answer,
        Instant answeredAt,
        Instant createdAt,
        Instant updatedAt,
        boolean masked
) {
    private static final String MASKED_BODY = "🔒 비밀글입니다.";

    /** viewer는 비로그인 시 null. 비밀글 열람 권한(작성자·관리자)이 없으면 본문/답변을 가린다. */
    public static InquiryResponse from(Inquiry i, AuthUser viewer) {
        boolean canView = !i.isSecret()
                || (viewer != null && (viewer.role() == Role.ADMIN || i.isOwnedBy(viewer.id())));
        boolean masked = !canView;
        return new InquiryResponse(
                i.getId(),
                i.getProductId(),
                i.getAuthorId(),
                i.getAuthor(),
                i.getTitle(),
                canView ? i.getContent() : MASKED_BODY,
                i.isSecret(),
                i.getStatus(),
                canView ? i.getAnswer() : null,
                canView ? i.getAnsweredAt() : null,
                i.getCreatedAt(),
                i.getUpdatedAt(),
                masked
        );
    }
}
