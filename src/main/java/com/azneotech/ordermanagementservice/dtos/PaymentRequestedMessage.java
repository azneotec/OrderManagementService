package com.azneotech.ordermanagementservice.dtos;

/**
 * Published on {@code payment-requested}: asks PaymentService for a payment link for an order.
 * {@code amountMinor} is in the currency's smallest unit (paise for INR), the unit PaymentService expects.
 */
public record PaymentRequestedMessage(
        String orderNumber,
        long amountMinor,
        String currency,
        String customerName,
        String email,
        String phoneNumber) {
}
