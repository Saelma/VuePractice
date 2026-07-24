package com.glassvue.domain.point.controller;

import com.glassvue.domain.point.dto.PointAccountResponse;
import com.glassvue.domain.point.dto.PointHistoryResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

/**
 * 적립금 · 회원 등급 API (2026-07-24, 백로그 C-10).
 *
 * <p>전부 <b>본인 것만</b> 다룬다 — 경로에 memberId 가 없고 토큰에서 읽는다.
 * SecurityConfig 의 기본이 {@code permitAll} 이라 매처를 빠뜨리면 <b>남의 적립금이 열린다</b>
 * (2026-07-23 쿠폰에서 겪은 자리라 401 을 계약으로 고정한다).
 *
 * <p>적립·차감 API 는 <b>없다.</b> 적립은 배송완료 이벤트가, 사용은 주문 생성이 한다 —
 * 외부에서 잔액을 직접 바꿀 경로를 만들면 이력과 어긋날 여지가 생긴다.
 */
@Tag(name = "Point", description = "적립금 · 회원 등급 API")
public interface PointController {

    @Operation(summary = "내 적립금 · 등급",
            description = "다음 등급까지 남은 구매액을 서버가 계산해 준다 — 화면이 등급 임계값을 알 필요가 없다.")
    ResponseEntity<ApiResponse<PointAccountResponse>> myAccount(
            @Parameter(hidden = true) AuthUser user);

    @Operation(summary = "내 적립금 이력 (최신순)",
            description = "amount 는 부호 있는 값이다(적립 +, 사용 −). balanceAfter 로 그때 잔액을 알 수 있다.")
    ResponseEntity<ApiResponse<PageResponse<PointHistoryResponse>>> myHistory(
            @Parameter(hidden = true) AuthUser user,
            @Parameter(hidden = true) Pageable pageable);
}
