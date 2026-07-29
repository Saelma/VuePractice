package com.glassvue.global.policy.controller;

import com.glassvue.global.policy.ShippingPolicy;
import com.glassvue.global.policy.dto.ShippingPolicyResponse;
import com.glassvue.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/policy")
public class ShippingPolicyControllerImpl implements ShippingPolicyController {

    private final ShippingPolicy shippingPolicy;

    // 공개 API — SecurityConfig 기본이 permitAll 이라 별도 매처가 필요 없다.
    // 노출되는 건 상점이 이미 화면에서 말하고 있는 값(배송비·무료배송 기준)뿐이라 민감정보가 아니다.
    // ⚠ 경로를 /api/policy/** 로 모은 이유는 GradePolicyController 주석 참조(보호 구역에 구멍을 안 뚫는다).
    @Override
    @GetMapping("/shipping")
    public ResponseEntity<ApiResponse<ShippingPolicyResponse>> shippingPolicy() {
        return ResponseEntity.ok(ApiResponse.ok(ShippingPolicyResponse.from(shippingPolicy)));
    }
}
