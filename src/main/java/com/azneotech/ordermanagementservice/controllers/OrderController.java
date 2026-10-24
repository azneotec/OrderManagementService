package com.azneotech.ordermanagementservice.controllers;

import com.azneotech.ordermanagementservice.dtos.OrderResponseDto;
import com.azneotech.ordermanagementservice.dtos.OrderSummaryResponseDto;
import com.azneotech.ordermanagementservice.dtos.OrderTrackingResponseDto;
import com.azneotech.ordermanagementservice.dtos.PageResponseDto;
import com.azneotech.ordermanagementservice.dtos.PlaceOrderRequestDto;
import com.azneotech.ordermanagementservice.dtos.UpdateOrderStatusRequestDto;
import com.azneotech.ordermanagementservice.security.AuthenticatedUser;
import com.azneotech.ordermanagementservice.services.IOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own orders; the user comes from the token, never from the URL. */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final IOrderService orderService;

    public OrderController(IOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponseDto> placeOrder(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @Valid @RequestBody PlaceOrderRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(principal.userId(), principal.email(), requestDto));
    }

    @GetMapping
    public ResponseEntity<PageResponseDto<OrderSummaryResponseDto>> getOrderHistory(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(orderService.getOrderHistory(principal.userId(), page, size));
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponseDto> getOrder(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrder(principal.userId(), orderNumber));
    }

    @GetMapping("/{orderNumber}/tracking")
    public ResponseEntity<OrderTrackingResponseDto> getTracking(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                @PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getTracking(principal.userId(), orderNumber));
    }

    /** ROLE_ADMIN only (see SecurityConfig): moves any customer's order along its delivery. */
    @PutMapping("/{orderNumber}/status")
    public ResponseEntity<OrderTrackingResponseDto> updateStatus(@PathVariable String orderNumber,
                                                                 @Valid @RequestBody UpdateOrderStatusRequestDto requestDto) {
        return ResponseEntity.ok(orderService.updateStatus(orderNumber, requestDto.getStatus(), requestDto.getNote()));
    }

}
