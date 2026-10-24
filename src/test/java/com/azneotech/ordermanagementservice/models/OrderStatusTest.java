package com.azneotech.ordermanagementservice.models;

import org.junit.jupiter.api.Test;

import static com.azneotech.ordermanagementservice.models.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void deliveryMovesForwardOneStepAtATime() {
        assertThat(CONFIRMED.canBeChangedByAdminTo(PROCESSING)).isTrue();
        assertThat(PROCESSING.canBeChangedByAdminTo(SHIPPED)).isTrue();
        assertThat(SHIPPED.canBeChangedByAdminTo(OUT_FOR_DELIVERY)).isTrue();
        assertThat(OUT_FOR_DELIVERY.canBeChangedByAdminTo(DELIVERED)).isTrue();

        assertThat(CONFIRMED.canBeChangedByAdminTo(SHIPPED)).isFalse();
        assertThat(SHIPPED.canBeChangedByAdminTo(PROCESSING)).isFalse();
    }

    @Test
    void anOrderCanBeCancelledUntilItShips() {
        assertThat(PENDING_PAYMENT.canBeChangedByAdminTo(CANCELLED)).isTrue();
        assertThat(CONFIRMED.canBeChangedByAdminTo(CANCELLED)).isTrue();
        assertThat(PROCESSING.canBeChangedByAdminTo(CANCELLED)).isTrue();

        assertThat(SHIPPED.canBeChangedByAdminTo(CANCELLED)).isFalse();
        assertThat(OUT_FOR_DELIVERY.canBeChangedByAdminTo(CANCELLED)).isFalse();
    }

    @Test
    void paymentOutcomesAreNeverSetByHand() {
        assertThat(PENDING_PAYMENT.canBeChangedByAdminTo(CONFIRMED)).isFalse();
        assertThat(PENDING_PAYMENT.canBeChangedByAdminTo(PAYMENT_FAILED)).isFalse();
    }

    @Test
    void finishedOrdersStayPut() {
        for (OrderStatus next : OrderStatus.values()) {
            assertThat(DELIVERED.canBeChangedByAdminTo(next)).isFalse();
            assertThat(CANCELLED.canBeChangedByAdminTo(next)).isFalse();
            assertThat(PAYMENT_FAILED.canBeChangedByAdminTo(next)).isFalse();
        }
    }

}
