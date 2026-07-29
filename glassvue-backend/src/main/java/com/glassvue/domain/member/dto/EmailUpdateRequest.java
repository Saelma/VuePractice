package com.glassvue.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 이메일 등록·변경(B-13). 여기서는 <b>비우는 것을 허용하지 않는다</b>({@code @NotBlank}) —
 * 지우기가 필요하면 별도 동작으로 만든다. "빈 문자열을 보내면 삭제"는 오타 한 번으로 주소가 사라진다.
 */
public record EmailUpdateRequest(

        @Schema(description = "새 이메일", example = "hong@example.com")
        @NotBlank @Email @Size(max = 255)
        String email
) {
}
