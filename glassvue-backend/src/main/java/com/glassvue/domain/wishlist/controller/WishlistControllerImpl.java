package com.glassvue.domain.wishlist.controller;

import com.glassvue.domain.wishlist.dto.WishlistItemResponse;
import com.glassvue.domain.wishlist.service.command.WishlistCommandService;
import com.glassvue.domain.wishlist.service.query.WishlistQueryService;
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
@RequestMapping("/api/wishlist")
public class WishlistControllerImpl implements WishlistController {

    private final WishlistCommandService commandService;
    private final WishlistQueryService queryService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<List<WishlistItemResponse>>> myWishlist(@LoginUser AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.myWishlist(user.id())));
    }

    @Override
    @GetMapping("/product-ids")
    public ResponseEntity<ApiResponse<List<UUID>>> myProductIds(@LoginUser AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.myProductIds(user.id())));
    }

    @Override
    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> add(@LoginUser AuthUser user, @PathVariable UUID productId) {
        commandService.add(user.id(), productId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> remove(@LoginUser AuthUser user, @PathVariable UUID productId) {
        commandService.remove(user.id(), productId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
