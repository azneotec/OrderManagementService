package com.azneotech.ordermanagementservice.exceptions;

/** The request is valid but cannot be applied to the order or catalog as they stand: unavailable products, a changed price, a status move that is not allowed. */
public class OrderConflictException extends RuntimeException {

    public OrderConflictException(String message) {
        super(message);
    }

}
