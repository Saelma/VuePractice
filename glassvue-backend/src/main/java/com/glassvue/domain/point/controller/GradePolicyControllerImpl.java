package com.glassvue.domain.point.controller;

import com.glassvue.domain.point.dto.GradePolicyResponse;
import com.glassvue.global.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/policy")
public class GradePolicyControllerImpl implements GradePolicyController {

    // 서비스가 없다 — enum 상수 표를 그대로 내보내는 것뿐이라 거쳐 갈 로직이 없다.
    @Override
    @GetMapping("/grades")
    public ResponseEntity<ApiResponse<List<GradePolicyResponse>>> grades() {
        return ResponseEntity.ok(ApiResponse.ok(GradePolicyResponse.all()));
    }
}
