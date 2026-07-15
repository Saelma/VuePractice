package com.glassvue.domain.notice.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import com.glassvue.domain.notice.dto.NoticeCreateRequest;
import com.glassvue.domain.notice.dto.NoticeResponse;
import com.glassvue.domain.notice.dto.NoticeSearchCondition;
import com.glassvue.domain.notice.dto.NoticeUpdateRequest;
import com.glassvue.domain.notice.service.command.NoticeCommandService;
import com.glassvue.domain.notice.service.query.NoticeQueryService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notices")
public class NoticeControllerImpl implements NoticeController {

    private final NoticeCommandService commandService;
    private final NoticeQueryService queryService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<UUID>> create(
            @LoginUser AuthUser user,
            @Valid @RequestBody NoticeCreateRequest request) {
        UUID id = commandService.create(request, user.id(), user.nickname());
        return ResponseEntity.created(URI.create("/api/notices/" + id))
                .body(ApiResponse.ok(id));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NoticeResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.get(id)));
    }

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NoticeResponse>>> search(
            NoticeSearchCondition condition, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.search(condition, pageable)));
    }

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> update(
            @LoginUser AuthUser user,
            @PathVariable UUID id, @Valid @RequestBody NoticeUpdateRequest request) {
        commandService.update(id, request, user.id());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @LoginUser AuthUser user,
            @PathVariable UUID id) {
        commandService.delete(id, user.id());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/{id}/views")
    public ResponseEntity<ApiResponse<Void>> increaseView(@PathVariable UUID id) {
        commandService.increaseView(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
