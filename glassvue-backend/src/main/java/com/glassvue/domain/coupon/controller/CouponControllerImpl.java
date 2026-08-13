package com.glassvue.domain.coupon.controller;

import com.glassvue.domain.coupon.dto.CouponCreateRequest;
import com.glassvue.domain.coupon.dto.CouponResponse;
import com.glassvue.domain.coupon.dto.EventCouponResponse;
import com.glassvue.domain.coupon.dto.MemberCouponResponse;
import com.glassvue.domain.coupon.service.CouponService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    /** 공개 엔드포인트 — SecurityConfig 에서 `/api/coupons/**` 인증 규칙보다 **위에** 예외로 뚫어 뒀다. */
    @Override
    @GetMapping("/coupons/welcome")
    public ResponseEntity<ApiResponse<CouponResponse>> welcome() {
        // 없으면 data:null — 화면이 "쿠폰 안내를 띄울지"를 이 값 하나로 판단한다.
        return ResponseEntity.ok(ApiResponse.ok(couponService.welcomeCoupon().orElse(null)));
    }

    /**
     * 공개 엔드포인트 — {@code /api/coupons/welcome} 과 같은 자리에 예외로 뚫어 뒀다.
     *
     * <p>⚠ {@code required = false} 라 비로그인이면 {@code user} 가 null 이다. 그때는 «이미 받았나» 를
     * 묻지 않고 {@code claimed=false} 로 답한다 — 비로그인 화면은 그 값을 쓰지 않는다.
     */
    @Override
    @GetMapping("/coupons/event")
    public ResponseEntity<ApiResponse<EventCouponResponse>> eventBanner(
            @LoginUser(required = false) AuthUser user) {
        UUID memberId = (user == null) ? null : user.id();
        // 없으면 data:null — 화면이 "배너를 그릴지"를 이 값 하나로 판단한다(welcome 과 같은 규약).
        return ResponseEntity.ok(ApiResponse.ok(couponService.eventBanner(memberId).orElse(null)));
    }

    @Override
    @PostMapping("/coupons/event/claim")
    public ResponseEntity<ApiResponse<UUID>> claimEvent(@LoginUser AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(couponService.claimEventCoupon(user.id())));
    }

    // 관리자 API는 /api/admin/** 아래로 모아 SecurityConfig 한 줄로 막는다(개별 매처를 잊을 수 없게).
    @Override
    @GetMapping("/admin/coupons")
    public ResponseEntity<ApiResponse<PageResponse<CouponResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(couponService.listAll(pageable)));
    }

    @Override
    @PostMapping("/admin/coupons")
    public ResponseEntity<ApiResponse<UUID>> create(@Valid @RequestBody CouponCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(couponService.create(request)));
    }

    // 지정/해제를 POST·DELETE 로 나눈다 — 회원 정지/해제(suspend·unsuspend)와 같은 결.
    @Override
    @PostMapping("/admin/coupons/{couponId}/welcome")
    public ResponseEntity<ApiResponse<Void>> designateWelcome(@PathVariable UUID couponId) {
        couponService.setWelcome(couponId, true);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @DeleteMapping("/admin/coupons/{couponId}/welcome")
    public ResponseEntity<ApiResponse<Void>> clearWelcome(@PathVariable UUID couponId) {
        couponService.setWelcome(couponId, false);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/admin/coupons/{couponId}/issue")
    public ResponseEntity<ApiResponse<UUID>> issue(@PathVariable UUID couponId,
                                                   @RequestParam UUID memberId) {
        return ResponseEntity.ok(ApiResponse.ok(couponService.issue(couponId, memberId)));
    }
}
