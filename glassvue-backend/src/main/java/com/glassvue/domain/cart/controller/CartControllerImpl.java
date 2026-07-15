package com.glassvue.domain.cart.controller;

import com.glassvue.domain.cart.dto.CartItemAddRequest;
import com.glassvue.domain.cart.dto.CartItemUpdateRequest;
import com.glassvue.domain.cart.dto.CartResponse;
import com.glassvue.domain.cart.service.CartService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cart")
public class CartControllerImpl implements CartController {

    private final CartService cartService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(@LoginUser AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.getCart(user.id())));
    }

    @Override
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<Void>> add(
            @LoginUser AuthUser user, @Valid @RequestBody CartItemAddRequest request) {
        cartService.add(user.id(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PatchMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<Void>> update(
            @LoginUser AuthUser user, @PathVariable UUID productId,
            @Valid @RequestBody CartItemUpdateRequest request) {
        cartService.setQuantity(user.id(), productId, request.quantity());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<Void>> remove(
            @LoginUser AuthUser user, @PathVariable UUID productId) {
        cartService.remove(user.id(), productId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clear(@LoginUser AuthUser user) {
        cartService.clear(user.id());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
