package com.azneotech.ordermanagementservice.dtos;

import com.azneotech.ordermanagementservice.models.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Delivery status of an order: where it is now and every step it went through, oldest first. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTrackingResponseDto {

    private String orderNumber;
    private OrderStatus status;
    private List<TrackingEventResponseDto> events;

}
