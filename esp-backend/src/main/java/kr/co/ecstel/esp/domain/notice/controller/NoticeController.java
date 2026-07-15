package kr.co.ecstel.esp.domain.notice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import kr.co.ecstel.esp.domain.notice.dto.NoticeCreateRequest;
import kr.co.ecstel.esp.domain.notice.dto.NoticeResponse;
import kr.co.ecstel.esp.domain.notice.dto.NoticeSearchCondition;
import kr.co.ecstel.esp.domain.notice.dto.NoticeUpdateRequest;
import kr.co.ecstel.esp.global.response.ApiResponse;
import kr.co.ecstel.esp.global.response.PageResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

/**
 * Swagger 문서는 이 인터페이스에, 매핑/구현은 NoticeControllerImpl에 둔다.
 */
@Tag(name = "Notice", description = "사내 공지 게시판 API")
public interface NoticeController {

    @Operation(summary = "공지 등록")
    ResponseEntity<ApiResponse<UUID>> create(@Valid NoticeCreateRequest request);

    @Operation(summary = "공지 단건 조회")
    ResponseEntity<ApiResponse<NoticeResponse>> get(UUID id);

    @Operation(summary = "공지 목록 검색 (제목·작성자·기간, 페이징)")
    ResponseEntity<ApiResponse<PageResponse<NoticeResponse>>> search(
            @ParameterObject NoticeSearchCondition condition,
            @ParameterObject Pageable pageable);

    @Operation(summary = "공지 수정")
    ResponseEntity<ApiResponse<Void>> update(UUID id, @Valid NoticeUpdateRequest request);

    @Operation(summary = "공지 삭제")
    ResponseEntity<ApiResponse<Void>> delete(UUID id);
}
