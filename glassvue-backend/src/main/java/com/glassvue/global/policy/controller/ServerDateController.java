package com.glassvue.global.policy.controller;

import com.glassvue.global.policy.dto.ServerDateResponse;
import com.glassvue.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Policy", description = "공개 정책·기준값")
public interface ServerDateController {

    @Operation(summary = "서버 기준 오늘(KST)",
            description = """
                    관리자 기간 선택의 프리셋(오늘·7일·30일·이번 달·지난 달)이 기준으로 삼는 날짜.
                    ⚠ 화면이 `new Date()` 로 계산하면 브라우저 시계가 기준이 되어, 같은 「이번 달」이
                    사람마다 다른 기간을 뜻하게 된다 — 장부는 KST 한 벌이라 기준도 한 곳이어야 한다(B-26).
                    비로그인에도 열려 있다: 날짜 하나이고 아무것도 드러내지 않는다.""")
    ResponseEntity<ApiResponse<ServerDateResponse>> today();
}
