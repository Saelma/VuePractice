package com.glassvue.domain.point.controller;

import com.glassvue.domain.point.dto.PointAccountResponse;
import com.glassvue.domain.point.dto.PointHistoryResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Point", description = "관리자 적립금 API (ADMIN 전용, B-11 회원 상세)")
public interface AdminPointController {

    @Operation(summary = "특정 회원의 적립금·등급",
            description = "회원 관리에서 그 회원의 잔액·누적구매·등급을 본다. 사용자용 /api/points/me 와 같은 데이터.")
    ResponseEntity<ApiResponse<PointAccountResponse>> account(@Parameter(description = "회원 id") UUID memberId);

    @Operation(summary = "특정 회원의 적립금 이력 (페이징)",
            description = "적립·사용·환불 원장. 최신순.")
    ResponseEntity<ApiResponse<PageResponse<PointHistoryResponse>>> history(
            @Parameter(description = "회원 id") UUID memberId,
            @ParameterObject Pageable pageable);
}
