package com.glassvue.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 아이디 찾기 요청 (2026-07-31, G-1) — 가입한 이메일로 아이디를 <b>메일로</b> 받는다.
 *
 * <p>응답 DTO 가 없는 이유: 돌려줄 것이 없다. 아이디를 응답에 실으면 <b>주소만 넣어 보며 가입 여부와
 * 아이디를 수집</b>할 수 있어(열거) 재설정에서 지킨 규칙과 정면으로 어긋난다. 가입 여부와 무관하게
 * 빈 성공 응답이고, 실제 값은 <b>그 주소의 주인만</b> 메일로 받는다.
 */
public record FindLoginIdRequest(

        @Schema(description = "가입할 때 등록한 이메일", example = "hong@example.com")
        @NotBlank @Email @Size(max = 255)
        String email
) {
}
