package com.glassvue.domain.inquiry.dto;

import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryStatus;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.global.security.AuthUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 문의 응답. 비밀글은 작성자·관리자가 아니면 본문/답변을 마스킹한다(masked=true).
 * {@code imageGroupId}는 노출하지 않고 이미지 목록만 내려준다(ReviewResponse와 같은 규약).
 * 마스킹되면 첨부 이미지도 숨긴다 — 안 그러면 비밀글 사진이 그대로 새어 나간다.
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
        List<ImageResponse> images,
        Instant createdAt,
        Instant updatedAt,
        boolean masked
) {
    private static final String MASKED_BODY = "🔒 비밀글입니다.";

    /**
     * viewer는 비로그인 시 null. 비밀글 열람 권한(작성자·관리자)이 없으면 본문/답변/이미지를 가린다.
     * images는 호출부가 그룹에서 조회해 넘긴다(마스킹 시 어차피 버려지지만, 조회 자체는 호출부 책임).
     */
    public static InquiryResponse from(Inquiry i, AuthUser viewer, List<ImageResponse> images) {
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
                canView ? images : List.of(),
                i.getCreatedAt(),
                i.getUpdatedAt(),
                masked
        );
    }
}
