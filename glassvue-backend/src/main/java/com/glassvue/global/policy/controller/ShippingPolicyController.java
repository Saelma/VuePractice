package com.glassvue.global.policy.controller;

import com.glassvue.global.policy.dto.ShippingPolicyResponse;
import com.glassvue.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * 상점 배송비 정책 조회 (공개).
 *
 * <p>⚠ <b>global 에 두는 첫 컨트롤러다.</b> 근거는 {@code ShippingPolicy} 가 global 에 있는 이유와 같다 —
 * 배송비는 cart·order 가 함께 읽는 <b>상점 전체 정책</b>이고, 어느 한 도메인에 넣으면 {@code cart ↔ order}
 * 순환이 생긴다. 노출 지점만 도메인으로 옮기면 <b>정책과 그 API 가 서로 다른 곳</b>에 앉게 되므로
 * 정책 옆에 둔다. (도메인 고유 정책이라면 그 도메인이 노출해야 한다 — 이건 예외지 기본이 아니다.)
 */
@Tag(name = "Policy", description = "상점 정책 API (공개)")
public interface ShippingPolicyController {

    @Operation(summary = "배송비 정책 조회",
            description = "기본 배송비와 무료배송 기준 금액. 화면이 정책 숫자를 하드코딩하지 않게 하기 위한 공개 API.")
    ResponseEntity<ApiResponse<ShippingPolicyResponse>> shippingPolicy();
}
