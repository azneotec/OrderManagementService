package com.azneotech.ordermanagementservice.exceptions;

/** The order request is well-formed but not acceptable, e.g. the same product listed twice. */
public class InvalidOrderRequestException extends RuntimeException {

    public InvalidOrderRequestException(String message) {
        super(message);
    }

}
