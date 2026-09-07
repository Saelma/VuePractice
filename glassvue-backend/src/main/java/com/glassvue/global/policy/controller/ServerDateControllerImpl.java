package com.glassvue.global.policy.controller;

import com.glassvue.global.common.KstDates;
import com.glassvue.global.policy.dto.ServerDateResponse;
import com.glassvue.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/policy")
public class ServerDateControllerImpl implements ServerDateController {

    @Override
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<ServerDateResponse>> today() {
        return ResponseEntity.ok(ApiResponse.ok(new ServerDateResponse(KstDates.today())));
    }
}
