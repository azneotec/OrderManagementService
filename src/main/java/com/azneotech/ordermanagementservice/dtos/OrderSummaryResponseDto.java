package com.azneotech.ordermanagementservice.dtos;

import com.azneotech.ordermanagementservice.models.OrderStatus;
import com.azneotech.ordermanagementservice.models.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the order history. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSummaryResponseDto {

    private String orderNumber;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private int totalQuantity;
    private BigDecimal totalAmount;
    private String currency;
    private Instant createdAt;

}
