package com.azneotech.ordermanagementservice.models;

/** Where an order is in its life, from payment through delivery; the tracking timeline is a log of these. */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_FAILED,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    /**
     * Whether an admin may move an order from this status to {@code next}. Delivery moves forward one step at a
     * time, and an order can be cancelled until it ships. Payment moves an order out of {@link #PENDING_PAYMENT}
     * (to {@link #CONFIRMED} or {@link #PAYMENT_FAILED}) through the {@code payment-result} topic, never by hand.
     */
    public boolean canBeChangedByAdminTo(OrderStatus next) {
        return switch (this) {
            case PENDING_PAYMENT -> next == CANCELLED;
            case CONFIRMED -> next == PROCESSING || next == CANCELLED;
            case PROCESSING -> next == SHIPPED || next == CANCELLED;
            case SHIPPED -> next == OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> next == DELIVERED;
            case PAYMENT_FAILED, DELIVERED, CANCELLED -> false;
        };
    }
}
