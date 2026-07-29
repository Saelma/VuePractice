package com.glassvue.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 이메일 인증번호 확인 (B-14).
 *
 * <p>⚠ 형식을 <b>숫자 6자리로 고정</b>한다 — 길이가 다른 값은 서버가 Redis 를 조회하기 전에 400 으로
 * 끊는다(무의미한 조회를 줄이고, 시도 횟수 카운터도 오염되지 않는다).
 */
public record EmailVerificationRequest(

        @Schema(description = "메일로 받은 6자리 인증번호", example = "482913")
        @NotBlank @Pattern(regexp = "\\d{6}", message = "인증번호는 6자리 숫자입니다.")
        String code
) {
}
