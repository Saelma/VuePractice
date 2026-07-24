package com.glassvue.domain.member.controller;

import com.glassvue.domain.member.dto.MemberAddressRequest;
import com.glassvue.domain.member.dto.MemberAddressResponse;
import com.glassvue.domain.member.service.command.MemberAddressCommandService;
import com.glassvue.domain.member.service.query.MemberAddressQueryService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members/me/addresses")
public class MemberAddressControllerImpl implements MemberAddressController {

    private final MemberAddressCommandService commandService;
    private final MemberAddressQueryService queryService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<List<MemberAddressResponse>>> myAddresses(@LoginUser AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.myAddresses(user.id())));
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<MemberAddressResponse>> add(
            @LoginUser AuthUser user,
            @Valid @RequestBody MemberAddressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(commandService.add(user.id(), request)));
    }

    @Override
    @PutMapping("/{addressId}")
    public ResponseEntity<ApiResponse<MemberAddressResponse>> update(
            @LoginUser AuthUser user,
            @PathVariable UUID addressId,
            @Valid @RequestBody MemberAddressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(commandService.update(user.id(), addressId, request)));
    }

    @Override
    @PatchMapping("/{addressId}/default")
    public ResponseEntity<ApiResponse<MemberAddressResponse>> setDefault(
            @LoginUser AuthUser user,
            @PathVariable UUID addressId) {
        return ResponseEntity.ok(ApiResponse.ok(commandService.setDefault(user.id(), addressId)));
    }

    @Override
    @DeleteMapping("/{addressId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @LoginUser AuthUser user,
            @PathVariable UUID addressId) {
        commandService.delete(user.id(), addressId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
