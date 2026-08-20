package com.glassvue.domain.coupon.controller;

import com.glassvue.domain.coupon.dto.CouponCreateRequest;
import com.glassvue.domain.coupon.dto.CouponResponse;
import com.glassvue.domain.coupon.dto.EventCouponResponse;
import com.glassvue.domain.coupon.dto.MemberCouponResponse;
import com.glassvue.domain.coupon.dto.PromotionCalendarResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "Coupon", description = "쿠폰 API")
public interface CouponController {

    @Operation(summary = "내 쿠폰 목록(미사용)",
            description = "itemsTotal(할인 전 상품합계)을 주면 쿠폰마다 지금 얼마 깎이는지·쓸 수 있는지를 함께 돌려준다. "
                    + "화면이 할인 규칙(정액/정률·상한·최소주문금액)을 알 필요가 없다.")
    ResponseEntity<ApiResponse<List<MemberCouponResponse>>> myCoupons(
            @Parameter(hidden = true) AuthUser user, long itemsTotal);

    @Operation(summary = "가입 쿠폰 안내 (공개)",
            description = "가입 즉시 자동 발급되는 쿠폰(G-2). **비로그인도 조회 가능** — 홈·가입 화면이 "
                    + "\"가입하면 쿠폰\" 문구를 띄울지 결정하는 근거다. 기능이 꺼져 있거나 설정된 쿠폰이 "
                    + "없으면 data 가 null 이고, 그때 화면은 문구를 감춘다(없는 혜택을 광고하지 않는다).")
    ResponseEntity<ApiResponse<CouponResponse>> welcome();

    @Operation(summary = "이벤트 쿠폰 배너 (공개)",
            description = "오늘 그릴 이벤트 배너(G-8). 오늘 진행 중이면 open=true(+ 로그인 시 claimed), "
                    + "앞으로 있으면 daysUntil 로 예고한다. **줄 게 없으면 data 가 null 이고 화면은 "
                    + "배너를 그리지 않는다.** 비로그인도 조회 가능하되 그 화면은 open=true 일 때만 쓴다 — "
                    + "예고를 보고 가입해도 그 날 다시 와야 받으므로 어긋난 약속이 된다.")
    ResponseEntity<ApiResponse<EventCouponResponse>> eventBanner(@Parameter(hidden = true) AuthUser user);

    @Operation(summary = "이벤트 쿠폰 받기",
            description = "발급 창이 열려 있는 이벤트 쿠폰을 한 장 발급받는다(G-8). **회원당 한 장** — "
                    + "이미 받았으면 COUPON-409I, 발급 창이 닫혀 있으면 COUPON-400C. "
                    + "동시에 두 번 눌러도 한 장만 나간다(유니크 인덱스 ux_member_coupon_once).")
    ResponseEntity<ApiResponse<UUID>> claimEvent(@Parameter(hidden = true) AuthUser user);

    @Operation(summary = "쿠폰 정의 목록 (관리자)",
            description = "발급 가능한 쿠폰(정의) 목록. 정렬 미지정 시 최신 생성순. 회원별 발급분이 아니라 쿠폰 그 자체다.")
    ResponseEntity<ApiResponse<PageResponse<CouponResponse>>> list(@ParameterObject Pageable pageable);

    @Operation(summary = "프로모션 달력 (관리자)",
            description = "그 달에 **살아 있는** 쿠폰의 기간 막대(B-27). 이벤트 쿠폰은 막대가 둘이다 — "
                    + "발급 창(ISSUE)과 사용 기간(USE). **겹치면 안 되는 것은 발급 창뿐이고 사용 기간은 "
                    + "겹치는 게 정상**이라 화면이 갈라 그린다. 날짜는 KST 로 잘라 「이 달의 며칠부터 "
                    + "며칠까지」로 준다 — 화면이 시간대 계산을 하면 보는 사람마다 막대가 하루씩 밀린다. "
                    + "month 를 비우면 이번 달(KST).")
    ResponseEntity<ApiResponse<PromotionCalendarResponse>> promotionCalendar(String month);

    @Operation(summary = "쿠폰 생성 (관리자)")
    ResponseEntity<ApiResponse<UUID>> create(AuthUser user, CouponCreateRequest request);

    @Operation(summary = "회원에게 쿠폰 발급 (관리자)")
    ResponseEntity<ApiResponse<UUID>> issue(AuthUser user, UUID couponId, UUID memberId);

    @Operation(summary = "가입 쿠폰으로 지정 (관리자)",
            description = "가입 즉시 자동 발급될 쿠폰으로 지정한다(V36). **전체에서 한 장만** 지정되며, "
                    + "다른 쿠폰이 이미 지정돼 있으면 그건 자동으로 해제된다. 재시작 없이 바로 반영된다.")
    ResponseEntity<ApiResponse<Void>> designateWelcome(AuthUser user, UUID couponId);

    @Operation(summary = "가입 쿠폰 지정 해제 (관리자)",
            description = "해제하면 가입 시 아무 쿠폰도 발급되지 않고, 홈·가입 화면의 안내 문구도 사라진다.")
    ResponseEntity<ApiResponse<Void>> clearWelcome(AuthUser user, UUID couponId);
}
