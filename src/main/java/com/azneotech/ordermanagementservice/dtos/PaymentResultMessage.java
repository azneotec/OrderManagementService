package com.azneotech.ordermanagementservice.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Consumed from {@code payment-result}; PaymentService publishes it for an order it was asked to collect for.
 * {@code paymentLink} accompanies {@link Status#LINK_CREATED}, {@code message} explains a {@link Status#FAILED}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentResultMessage(String orderNumber, Status status, String paymentLink, String message) {

    public enum Status {
        /** The customer can now pay at {@code paymentLink}. */
        LINK_CREATED,
        PAID,
        FAILED
    }

}
