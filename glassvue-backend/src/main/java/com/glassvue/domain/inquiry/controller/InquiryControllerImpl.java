package com.glassvue.domain.inquiry.controller;

import com.glassvue.domain.inquiry.dto.InquiryAnswerRequest;
import com.glassvue.domain.inquiry.dto.InquiryCreateRequest;
import com.glassvue.domain.inquiry.dto.InquiryResponse;
import com.glassvue.domain.inquiry.dto.InquiryUpdateRequest;
import com.glassvue.domain.inquiry.service.command.InquiryCommandService;
import com.glassvue.domain.inquiry.service.query.InquiryQueryService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.response.PageResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
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
@RequestMapping("/api")
public class InquiryControllerImpl implements InquiryController {

    private final InquiryCommandService commandService;
    private final InquiryQueryService queryService;

    @Override
    @PostMapping("/products/{productId}/inquiries")
    public ResponseEntity<ApiResponse<UUID>> create(
            @LoginUser AuthUser user,
            @PathVariable UUID productId,
            @Valid @RequestBody InquiryCreateRequest request) {
        UUID id = commandService.create(productId, request, user);
        return ResponseEntity.created(URI.create("/api/inquiries/" + id)).body(ApiResponse.ok(id));
    }

    @Override
    @GetMapping("/products/{productId}/inquiries")
    public ResponseEntity<ApiResponse<PageResponse<InquiryResponse>>> list(
            @LoginUser(required = false) AuthUser viewer,
            @PathVariable UUID productId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getProductInquiries(productId, pageable, viewer)));
    }

    @Override
    @PutMapping("/inquiries/{id}")
    public ResponseEntity<ApiResponse<Void>> update(
            @LoginUser AuthUser user,
            @PathVariable UUID id,
            @Valid @RequestBody InquiryUpdateRequest request) {
        commandService.update(id, request, user);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @DeleteMapping("/inquiries/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@LoginUser AuthUser user, @PathVariable UUID id) {
        commandService.delete(id, user);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/inquiries/{id}/answer")
    public ResponseEntity<ApiResponse<Void>> answer(
            @LoginUser AuthUser user,
            @PathVariable UUID id, @Valid @RequestBody InquiryAnswerRequest request) {
        commandService.answer(id, request, user);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
