package com.glassvue.domain.inquiry.dto;

import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryStatus;
import com.glassvue.domain.inquiry.entity.InquiryType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 내 문의 목록의 한 줄 (2026-08-07, G-3 3단계).
 *
 * <p>🔴 <b>이 화면이 없으면 2단계가 성립하지 않는다.</b> 답변 알림(B-15)은 링크가
 * {@code /products/{productId}#inquiries} 하나뿐인데, 일반 문의는 productId 가 없어
 * <b>알림이 착지할 URL 자체가 없다.</b> 즉 「내 문의」는 편의 기능이 아니라 <b>일반 문의의 주소</b>다.
 *
 * <p>🔴 <b>마스킹하지 않는다</b> — 조회 조건이 {@code authorId = 나} 라 <b>구조적으로 전부 내 글</b>이다.
 * 비밀글 마스킹은 «남의 글을 보는 사람» 을 위한 규칙이라 여기선 적용될 여지가 없다.
 * ({@link InquiryResponse} 를 재사용했다면 {@code viewer} 를 넘겨 매 줄마다 «내가 작성자인가» 를
 * 다시 판정했을 텐데, 그건 <b>이미 참인 것을 한 번 더 묻는</b> 일이다.)
 *
 * <p>⚠ <b>{@code secret} 은 그대로 싣는다.</b> 가리기 위해서가 아니라, 내가 <b>비밀로 물었다는 사실</b>을
 * 화면에서 알아야 하기 때문이다(공개글로 착각하면 다음 문의를 비밀글로 안 쓴다).
 *
 * @param type        유형. 상품 문의(PRODUCT)와 일반 문의가 <b>한 목록에 섞여</b> 오므로 줄마다 필요하다 —
 *                    가르지 않는 것이 이 목록의 요점이다({@code findByAuthor} 주석).
 * @param productName 상품 문의일 때만 채워진다. 일반 문의면 null 이고, 상품이 지워졌어도 null 이다
 *                    (문의는 느슨한 참조라 함께 안 지워진다). ⚠ <b>둘 다 null 이라 화면에서 구분되지
 *                    않는다</b> — 그래서 {@code type} 을 함께 본다: PRODUCT 인데 이름이 없으면 «지워진 상품».
 */
public record MyInquiryResponse(
        UUID id,
        InquiryType type,
        UUID productId,
        String productName,
        String title,
        String content,
        boolean secret,
        InquiryStatus status,
        String answer,
        Instant answeredAt,
        List<ImageResponse> images,
        Instant createdAt,
        Instant updatedAt
) {
    public static MyInquiryResponse from(Inquiry i, String productName, List<ImageResponse> images) {
        return new MyInquiryResponse(
                i.getId(),
                i.getType(),
                i.getProductId(),
                productName,
                i.getTitle(),
                i.getContent(),
                i.isSecret(),
                i.getStatus(),
                i.getAnswer(),
                i.getAnsweredAt(),
                images,
                i.getCreatedAt(),
                i.getUpdatedAt());
    }
}
