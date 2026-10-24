package com.azneotech.ordermanagementservice.models;

public enum PaymentStatus {
    /** Online payment requested, no result yet. */
    PENDING,
    PAID,
    FAILED,
    /** Cash on delivery: nothing to collect until the parcel arrives. */
    PAY_ON_DELIVERY
}
