package com.glassvue.domain.order.controller;

import com.glassvue.domain.order.dto.OrderResponse;
import com.glassvue.domain.order.service.OrderService;
import com.glassvue.global.response.ApiResponse;
import com.glassvue.global.security.AuthUser;
import com.glassvue.global.security.LoginUser;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderControllerImpl implements OrderController {

    private final OrderService orderService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<UUID>> checkout(@LoginUser AuthUser user) {
        UUID id = orderService.checkout(user.id());
        return ResponseEntity.created(URI.create("/api/orders/" + id)).body(ApiResponse.ok(id));
    }

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> myOrders(@LoginUser AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.myOrders(user.id())));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> get(@LoginUser AuthUser user, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.get(id, user)));
    }

    @Override
    @PostMapping("/{id}/pay")
    public ResponseEntity<ApiResponse<Void>> pay(@LoginUser AuthUser user, @PathVariable UUID id) {
        orderService.pay(id, user.id());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<Void>> ship(@PathVariable UUID id) {
        orderService.ship(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Override
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@LoginUser AuthUser user, @PathVariable UUID id) {
        orderService.cancel(id, user.id());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
