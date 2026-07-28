package com.glassvue.domain.member.dto;

import com.glassvue.domain.member.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 관리자 역할 변경(B-11 후속). */
public record RoleChangeRequest(

        @Schema(description = "변경할 역할", example = "ADMIN")
        @NotNull
        Role role
) {
}
