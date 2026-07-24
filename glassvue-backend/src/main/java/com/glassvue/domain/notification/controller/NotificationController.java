package com.glassvue.domain.notification.controller;

import com.glassvue.domain.notification.dto.NotificationResponse;
import com.glassvue.domain.notification.dto.NotificationSettingRequest;
import com.glassvue.domain.notification.dto.NotificationSettingResponse;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Notification", description = "인앱 알림 API")
public interface NotificationController {

    @Operation(summary = "실시간 알림 스트림(SSE) — 새 알림을 즉시 밀어 준다")
    SseEmitter stream(AuthUser user);

    @Operation(summary = "내 알림 목록(페이징, 최신순)")
    ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            AuthUser user, @ParameterObject Pageable pageable);

    @Operation(summary = "안읽음 알림 수 — 벨 뱃지")
    ResponseEntity<ApiResponse<Long>> unreadCount(AuthUser user);

    @Operation(summary = "알림 읽음 처리(본인)")
    ResponseEntity<ApiResponse<Void>> read(AuthUser user, UUID id);

    @Operation(summary = "모두 읽음")
    ResponseEntity<ApiResponse<Void>> readAll(AuthUser user);

    @Operation(summary = "내 알림 설정(타입별 on/off) 조회")
    ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> settings(AuthUser user);

    @Operation(summary = "알림 타입 켜기/끄기")
    ResponseEntity<ApiResponse<Void>> updateSetting(AuthUser user, @Valid NotificationSettingRequest request);
}
