package com.glassvue.domain.notification.controller;

import com.glassvue.domain.notification.dto.NotificationResponse;
import com.glassvue.domain.notification.dto.NotificationSettingRequest;
import com.glassvue.domain.notification.dto.NotificationSettingResponse;
import com.glassvue.domain.notification.service.NotificationCommandService;
import com.glassvue.domain.notification.service.NotificationQueryService;
import com.glassvue.domain.notification.sse.NotificationStream;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationControllerImpl implements NotificationController {

    private final NotificationCommandService commandService;
    private final NotificationQueryService queryService;
    private final NotificationStream stream;

    @Override
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@LoginUser AuthUser user) {
        // SSE 는 ApiResponse 로 감싸지 않는다 — 스트림 자체가 응답이다(JSON 한 건이 아니다).
        return stream.subscribe(user.id());
    }

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            @LoginUser AuthUser user, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.myNotifications(user.id(), pageable)));
    }

    @Override
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> unreadCount(@LoginUser AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.unreadCount(user.id())));
    }

    @Override
    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> read(@LoginUser AuthUser user, @PathVariable UUID id) {
        commandService.markRead(id, user.id());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> readAll(@LoginUser AuthUser user) {
        commandService.markAllRead(user.id());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> settings(@LoginUser AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.settings(user.id())));
    }

    @Override
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<Void>> updateSetting(
            @LoginUser AuthUser user, @Valid @RequestBody NotificationSettingRequest request) {
        commandService.changeSetting(user.id(), request.type(), request.enabled());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
