package com.glassvue.domain.restock.controller;

import com.glassvue.domain.restock.service.command.RestockSubscriptionCommandService;
import com.glassvue.domain.restock.service.query.RestockSubscriptionQueryService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/restock")
public class RestockControllerImpl implements RestockController {

    private final RestockSubscriptionCommandService commandService;
    private final RestockSubscriptionQueryService queryService;

    @Override
    @GetMapping("/product-ids")
    public ResponseEntity<ApiResponse<List<UUID>>> myProductIds(@LoginUser AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.myProductIds(user.id())));
    }

    @Override
    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> subscribe(@LoginUser AuthUser user, @PathVariable UUID productId) {
        commandService.subscribe(user.id(), productId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@LoginUser AuthUser user, @PathVariable UUID productId) {
        commandService.unsubscribe(user.id(), productId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
