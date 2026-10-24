package com.azneotech.ordermanagementservice.dtos;

import com.azneotech.ordermanagementservice.models.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceOrderRequestDto {

    @NotEmpty
    @Size(max = 50)
    @Valid
    private List<OrderItemRequestDto> items;
    @NotNull
    @Valid
    private DeliveryAddressDto deliveryAddress;
    @NotNull
    private PaymentMethod paymentMethod;
    /**
     * Optional: the total the customer was quoted (CartService sends its checkout total). The order is priced from
     * the catalog again; if that price differs, the order is refused with 409 instead of charging a different amount.
     */
    @Positive
    private BigDecimal expectedTotal;

}
