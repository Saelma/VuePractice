package com.glassvue.domain.inquiry.dto;

import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryStatus;
import com.glassvue.domain.inquiry.entity.InquiryType;
import java.time.Instant;
import java.util.UUID;

/**
 * 관리자 문의 목록의 한 줄 (2026-08-06, 백로그 G-3).
 *
 * <p>이 DTO 가 생기기 전까지 <b>관리자 문의 API 는 0개</b>였다(실측). 관리자는 <b>상품 상세에
 * 들어가야만</b> 문의에 답할 수 있었고, 그래서 상품과 무관한 문의는 넣어 봐야 <b>답할 경로가 없었다.</b>
 *
 * <p>{@link InquiryResponse} 와 <b>따로 두는 이유</b>는 답할 질문이 다르기 때문이다(관리자 리뷰와 같은
 * 판단). 고객 화면은 *"이 상품에 뭘 물어봤나"* 를 묻고 상품이 이미 정해져 있지만, 관리자 목록은
 * *"지금 답할 게 뭐가 남았나"* 를 묻고 <b>여러 상품을 가로질러</b> 본다.
 *
 * <p>🔴 <b>마스킹하지 않는다.</b> {@code InquiryResponse} 는 비밀글 본문을 가리지만, 그건 «볼 권한이
 * 없는 사람» 을 위한 규칙이고 관리자는 거기서도 열람 대상이다({@code Role.ADMIN} 갈래). 관리자 목록에서
 * 본문을 가리면 <b>답을 쓰라면서 질문을 안 보여주는</b> 꼴이 된다. 대신 {@code secret} 을 실어
 * 화면이 «비밀글» 이라고 표시하게 한다 — 답변이 공개되지 않는다는 걸 관리자가 알아야 하기 때문이다.
 *
 * <p>⚠ <b>첨부 이미지는 싣지 않는다</b>(관리자 리뷰 목록과 같은 이유). 목록 한 번에 그룹 조회가
 * 따라붙는다. 사진까지 봐야 하면 상품 상세에서 본다.
 *
 * @param productId   ⚠ <b>null 일 수 있다</b> — G-3 2단계에서 상품과 무관한 «일반 문의» 가 들어온다.
 * @param productName 상품명 — <b>조회 시점 값</b>이다(스냅샷이 아니다). 상품 문의가 아니거나 상품을
 *                    못 찾으면 null 이고, 그때도 <b>줄은 남는다</b> — 목록에서 빠지면 관리자는
 *                    답할 대상이 있다는 사실 자체를 모르게 된다.
 */
public record AdminInquiryResponse(
        UUID id,
        InquiryType type,
        UUID productId,
        String productName,
        UUID authorId,
        String author,
        String title,
        String content,
        boolean secret,
        InquiryStatus status,
        String answer,
        Instant answeredAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminInquiryResponse from(Inquiry i, String productName) {
        return new AdminInquiryResponse(
                i.getId(),
                i.getType(),
                i.getProductId(),
                productName,
                i.getAuthorId(),
                i.getAuthor(),
                i.getTitle(),
                i.getContent(),
                i.isSecret(),
                i.getStatus(),
                i.getAnswer(),
                i.getAnsweredAt(),
                i.getCreatedAt(),
                i.getUpdatedAt());
    }
}
