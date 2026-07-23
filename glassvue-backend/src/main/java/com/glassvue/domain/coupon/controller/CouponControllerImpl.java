package com.glassvue.domain.coupon.controller;

import com.glassvue.domain.coupon.dto.CouponCreateRequest;
import com.glassvue.domain.coupon.dto.MemberCouponResponse;
import com.glassvue.domain.coupon.service.CouponService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CouponControllerImpl implements CouponController {

    private final CouponService couponService;

    @Override
    @GetMapping("/coupons/me")
    public ResponseEntity<ApiResponse<List<MemberCouponResponse>>> myCoupons(
            @LoginUser AuthUser user,
            @RequestParam(defaultValue = "0") long itemsTotal) {
        return ResponseEntity.ok(ApiResponse.ok(couponService.myCoupons(user.id(), itemsTotal)));
    }

    // 관리자 API는 /api/admin/** 아래로 모아 SecurityConfig 한 줄로 막는다(개별 매처를 잊을 수 없게).
    @Override
    @PostMapping("/admin/coupons")
    public ResponseEntity<ApiResponse<UUID>> create(@Valid @RequestBody CouponCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(couponService.create(request)));
    }

    @Override
    @PostMapping("/admin/coupons/{couponId}/issue")
    public ResponseEntity<ApiResponse<UUID>> issue(@PathVariable UUID couponId,
                                                   @RequestParam UUID memberId) {
        return ResponseEntity.ok(ApiResponse.ok(couponService.issue(couponId, memberId)));
    }
}
