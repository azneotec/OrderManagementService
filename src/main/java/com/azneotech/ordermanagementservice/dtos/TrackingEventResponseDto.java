package com.azneotech.ordermanagementservice.dtos;

import com.azneotech.ordermanagementservice.models.OrderStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrackingEventResponseDto {

    private OrderStatus status;
    private String note;
    private Instant timestamp;

}
