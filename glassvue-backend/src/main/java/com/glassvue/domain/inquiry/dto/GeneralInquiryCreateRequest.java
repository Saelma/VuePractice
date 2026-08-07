package com.glassvue.domain.inquiry.dto;

import com.glassvue.domain.inquiry.entity.InquiryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * 일반 고객센터 문의 작성 (2026-08-07, G-3 2단계).
 *
 * <p>⚠ <b>{@link InquiryCreateRequest} 를 재사용하지 않는다.</b> 그쪽에 {@code type} 을 넣어
 * 공용으로 쓰면, 상품 경로로 {@code type=DELIVERY} 를 보냈을 때 서버가 <b>조용히 PRODUCT 로
 * 덮어쓴다</b> — 요청은 200 이고 화면도 멀쩡한데 <b>보낸 값이 사라진다.</b> 무시되는 입력 필드는
 * 있으나 마나가 아니라 <b>거짓말</b>이라, 아예 받지 않는 편이 낫다.
 * (관리자 DTO 를 고객 DTO 와 나눈 2026-08-06 의 판단과 같은 이유다.)
 *
 * <p>나머지 필드는 상품 문의와 <b>같게</b> 맞춘다 — 길이가 갈리면 한쪽에서 되던 입력이
 * 다른 쪽에서 거부된다(WA §2-2-1 이 경계하는 자리와 같은 종류).
 */
public record GeneralInquiryCreateRequest(

        @Schema(description = "문의 유형. PRODUCT 는 받지 않는다(상품 문의는 상품 페이지에서)",
                example = "DELIVERY")
        @NotNull
        InquiryType type,

        @Schema(description = "제목", example = "주문한 상품이 아직 안 왔어요")
        @NotBlank @Size(max = 200)
        String title,

        @Schema(description = "문의 내용", example = "3일 전에 결제했는데 배송 시작이 안 됩니다.")
        @NotBlank @Size(max = 2000)
        String content,

        @Schema(description = "비밀글 여부(작성자·관리자만 열람)", example = "true")
        boolean secret,

        @Schema(description = "첨부 이미지 id 목록(업로드 후 받은 id). 없으면 빈 배열/생략")
        @Size(max = 5, message = "문의 이미지는 최대 5장입니다")
        List<UUID> imageIds
) {
}
