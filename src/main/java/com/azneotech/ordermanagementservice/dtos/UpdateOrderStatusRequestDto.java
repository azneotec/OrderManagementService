package com.azneotech.ordermanagementservice.dtos;

import com.azneotech.ordermanagementservice.models.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequestDto {

    @NotNull
    private OrderStatus status;
    /** Shown on the customer's timeline, e.g. a carrier name or tracking reference. */
    @Size(max = 255)
    private String note;

}
