package com.azneotech.ordermanagementservice.dtos;

import com.azneotech.ordermanagementservice.models.OrderStatus;
import com.azneotech.ordermanagementservice.models.PaymentMethod;
import com.azneotech.ordermanagementservice.models.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The order confirmation: everything the customer bought, where it goes and how it is paid. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderResponseDto {

    private String orderNumber;
    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    /** Where to pay an online order; absent for cash on delivery and until PaymentService has created the link. */
    private String paymentLink;
    private String currency;
    private List<OrderItemResponseDto> items;
    private int totalQuantity;
    private BigDecimal totalAmount;
    private DeliveryAddressDto deliveryAddress;
    private Instant createdAt;
    private Instant updatedAt;

}
