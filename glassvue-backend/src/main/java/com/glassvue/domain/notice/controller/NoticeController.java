package com.glassvue.domain.notice.controller;

import com.glassvue.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import com.glassvue.domain.notice.dto.NoticeCreateRequest;
import com.glassvue.domain.notice.dto.NoticeResponse;
import com.glassvue.domain.notice.dto.NoticeSearchCondition;
import com.glassvue.domain.notice.dto.NoticeUpdateRequest;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

/**
 * Swagger 문서는 이 인터페이스에, 매핑/구현은 NoticeControllerImpl에 둔다.
 */
@Tag(name = "Notice", description = "사내 공지 게시판 API")
public interface NoticeController {

    @Operation(summary = "공지 등록 (로그인 필요, 작성자=로그인 유저)")
    ResponseEntity<ApiResponse<UUID>> create(
            @Parameter(hidden = true) AuthUser user,
            @Valid NoticeCreateRequest request);

    @Operation(summary = "공지 단건 조회")
    ResponseEntity<ApiResponse<NoticeResponse>> get(UUID id);

    @Operation(summary = "공지 목록 검색 (제목·작성자·기간, 페이징)")
    ResponseEntity<ApiResponse<PageResponse<NoticeResponse>>> search(
            @ParameterObject NoticeSearchCondition condition,
            @ParameterObject Pageable pageable);

    @Operation(summary = "공지 수정 (본인 글만)")
    ResponseEntity<ApiResponse<Void>> update(
            @Parameter(hidden = true) AuthUser user,
            UUID id, @Valid NoticeUpdateRequest request);

    @Operation(summary = "공지 삭제 (본인 글만)")
    ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(hidden = true) AuthUser user,
            UUID id);

    @Operation(summary = "공지 조회수 증가")
    ResponseEntity<ApiResponse<Void>> increaseView(UUID id);
}
