package com.azneotech.ordermanagementservice.exceptions;

/** No order with that number exists for the caller (someone else's order looks the same as a missing one). */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderNumber) {
        super("Order " + orderNumber + " does not exist");
    }

}
