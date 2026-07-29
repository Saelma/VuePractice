package com.glassvue.domain.point.controller;

import com.glassvue.domain.point.dto.GradePolicyResponse;
import com.glassvue.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

/**
 * 등급 정책 표 (공개) — 2026-07-29.
 *
 * <p>⚠ <b>왜 {@code /api/points/**} 가 아니라 {@code /api/policy/**} 인가.</b>
 * {@code /api/points/**} 는 SecurityConfig 가 <b>한 줄로 authenticated</b> 로 막는 구역이다
 * (기본이 permitAll 이라 매처를 빠뜨리면 남의 적립금이 열리는 자리라서). 거기에 "이 하나만 공개" 예외를
 * 얹으면 <b>보호 구역 안에 구멍을 뚫는 것</b>이고, 매처 순서에 의존하게 된다.
 *
 * <p>감사 로그({@code /api/admin/audit/**} 를 {@code /api/admin/**} 위에 둔 것)와 방향이 반대라는 점이 핵심이다 —
 * 그건 <b>좁히는</b> 예외(ADMIN → SUPER_ADMIN)라 실수해도 더 잠기지만, 이건 <b>넓히는</b> 예외라 실수하면 열린다.
 * 그래서 공개 정책은 <b>처음부터 공개인 네임스페이스</b>로 모은다 — 여기엔 회원별 정보가 절대 들어오지 않는다.
 *
 * <p>클래스가 point 도메인에 있는 것은 그대로다 — 등급은 point 가 소유한다(경로만 공개 구역).
 */
@Tag(name = "Policy", description = "상점 정책 API (공개)")
public interface GradePolicyController {

    @Operation(summary = "회원 등급 정책 표",
            description = "등급별 기준 누적구매액·적립률. 비로그인 화면이 \"최대 N% 적립\"을 직접 적지 않게 "
                    + "하려고 연다 — 정책이 바뀌면 안내 문구도 따라 바뀐다. 회원별 정보는 없다.")
    ResponseEntity<ApiResponse<List<GradePolicyResponse>>> grades();
}
