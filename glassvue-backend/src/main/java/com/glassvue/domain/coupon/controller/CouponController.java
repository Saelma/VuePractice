package com.glassvue.domain.coupon.controller;

import com.glassvue.domain.coupon.dto.CouponCreateRequest;
import com.glassvue.domain.coupon.dto.MemberCouponResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Coupon", description = "쿠폰 API")
public interface CouponController {

    @Operation(summary = "내 쿠폰 목록(미사용)",
            description = "itemsTotal(할인 전 상품합계)을 주면 쿠폰마다 지금 얼마 깎이는지·쓸 수 있는지를 함께 돌려준다. "
                    + "화면이 할인 규칙(정액/정률·상한·최소주문금액)을 알 필요가 없다.")
    ResponseEntity<ApiResponse<List<MemberCouponResponse>>> myCoupons(
            @Parameter(hidden = true) AuthUser user, long itemsTotal);

    @Operation(summary = "쿠폰 생성 (관리자)")
    ResponseEntity<ApiResponse<UUID>> create(CouponCreateRequest request);

    @Operation(summary = "회원에게 쿠폰 발급 (관리자)")
    ResponseEntity<ApiResponse<UUID>> issue(UUID couponId, UUID memberId);
}
