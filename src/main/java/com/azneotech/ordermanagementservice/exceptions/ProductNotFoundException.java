package com.azneotech.ordermanagementservice.exceptions;

/** The catalog has no product with this id. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long productId) {
        super("Product " + productId + " does not exist");
    }

}
