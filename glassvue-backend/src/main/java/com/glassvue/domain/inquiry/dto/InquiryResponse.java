package com.glassvue.domain.inquiry.dto;

import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryStatus;
import com.glassvue.global.common.AuthorNames;
import com.glassvue.global.security.AuthUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 문의 응답. 비밀글은 작성자·관리자가 아니면 제목/본문/답변을 마스킹한다(masked=true).
 * {@code imageGroupId}는 노출하지 않고 이미지 목록만 내려준다(ReviewResponse와 같은 규약).
 * 마스킹되면 첨부 이미지도 숨긴다 — 안 그러면 비밀글 사진이 그대로 새어 나간다.
 *
 * <p>🔴 <b>{@code authorId}(회원 UUID)를 싣지 않는다</b>(2026-09-10, R 축). 이 경로는 <b>비로그인도
 * 부를 수 있고</b>, 화면이 그 값으로 하는 일은 «내 글인가» 하나뿐이라 <b>{@code mine} 한 칸이면 된다</b>.
 * PK 가 UUIDv7 이라 앞 48비트가 <b>생성 시각(=가입 시각)</b>이다 — 원시 id 를 내보내면 임의 회원의
 * 가입 시각이 토큰 없이 밀리초까지 새어 나간다(실측으로 세 명의 가입 시각을 복원했다).
 *
 * <p>⚠ <b>제목도 가린다.</b> 화면이 «비밀글 (작성자·판매자만 열람)» 이라고 약속하는데
 * 본문만 가리면 제목이 그 약속을 깬다 — 제목은 대개 질문 그 자체다.
 *
 * <p>{@code author} 는 본인·관리자가 아니면 가린다 — 공개·비밀 가리지 않고({@link AuthorNames}, R-7).
 */
public record InquiryResponse(
        UUID id,
        UUID productId,
        boolean mine,
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
    private static final String MASKED_TITLE = "🔒 비밀글입니다.";

    /**
     * viewer는 비로그인 시 null. 비밀글 열람 권한(작성자·관리자)이 없으면 제목/본문/답변/이미지를 가린다.
     * images는 호출부가 그룹에서 조회해 넘긴다(마스킹 시 어차피 버려지지만, 조회 자체는 호출부 책임).
     *
     * <p>⚠ {@code mine} 은 <b>관리자여도 남의 글이면 false</b> 다 — «내 글인가» 와 «볼 수 있나» 는
     * 다른 질문이고, 화면은 관리자 여부를 따로 안다.
     */
    public static InquiryResponse from(Inquiry i, AuthUser viewer, List<ImageResponse> images) {
        boolean mine = viewer != null && i.isOwnedBy(viewer.id());
        boolean canView = !i.isSecret() || mine || (viewer != null && viewer.isAdmin());
        boolean masked = !canView;
        return new InquiryResponse(
                i.getId(),
                i.getProductId(),
                mine,
                AuthorNames.forViewer(i.getAuthor(), mine, viewer),
                canView ? i.getTitle() : MASKED_TITLE,
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
