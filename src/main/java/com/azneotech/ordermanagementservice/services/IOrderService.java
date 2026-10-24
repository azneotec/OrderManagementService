package com.azneotech.ordermanagementservice.services;

import com.azneotech.ordermanagementservice.dtos.OrderResponseDto;
import com.azneotech.ordermanagementservice.dtos.OrderSummaryResponseDto;
import com.azneotech.ordermanagementservice.dtos.OrderTrackingResponseDto;
import com.azneotech.ordermanagementservice.dtos.PageResponseDto;
import com.azneotech.ordermanagementservice.dtos.PaymentResultMessage;
import com.azneotech.ordermanagementservice.dtos.PlaceOrderRequestDto;
import com.azneotech.ordermanagementservice.models.OrderStatus;

public interface IOrderService {

    /**
     * Prices the items from the catalog and places the order: cash on delivery is confirmed at once, an online
     * payment waits in {@code PENDING_PAYMENT} until PaymentService reports the result.
     *
     * @throws com.azneotech.ordermanagementservice.exceptions.InvalidOrderRequestException if a product is listed twice
     * @throws com.azneotech.ordermanagementservice.exceptions.OrderConflictException if a product is gone or the total differs from {@code expectedTotal}
     * @throws com.azneotech.ordermanagementservice.exceptions.DownstreamUnavailableException if the catalog cannot answer
     */
    OrderResponseDto placeOrder(Long userId, String customerEmail, PlaceOrderRequestDto request);

    /** The caller's orders, newest first; {@code page} is zero-based. */
    PageResponseDto<OrderSummaryResponseDto> getOrderHistory(Long userId, int page, int size);

    /** @throws com.azneotech.ordermanagementservice.exceptions.OrderNotFoundException if the caller has no such order */
    OrderResponseDto getOrder(Long userId, String orderNumber);

    /** @throws com.azneotech.ordermanagementservice.exceptions.OrderNotFoundException if the caller has no such order */
    OrderTrackingResponseDto getTracking(Long userId, String orderNumber);

    /**
     * Admin: moves any order to its next delivery status (see {@link OrderStatus#canBeChangedByAdminTo}) and
     * emails the customer.
     *
     * @throws com.azneotech.ordermanagementservice.exceptions.OrderNotFoundException if there is no such order
     * @throws com.azneotech.ordermanagementservice.exceptions.OrderConflictException if the move is not allowed
     */
    OrderTrackingResponseDto updateStatus(String orderNumber, OrderStatus newStatus, String note);

    /** Applies a PaymentService result to its order. Safe to apply twice; results for unknown orders are ignored. */
    void handlePaymentResult(PaymentResultMessage result);

}
