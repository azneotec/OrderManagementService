package com.azneotech.ordermanagementservice.mappers;

import com.azneotech.ordermanagementservice.dtos.DeliveryAddressDto;
import com.azneotech.ordermanagementservice.dtos.OrderItemResponseDto;
import com.azneotech.ordermanagementservice.dtos.OrderResponseDto;
import com.azneotech.ordermanagementservice.dtos.OrderSummaryResponseDto;
import com.azneotech.ordermanagementservice.dtos.OrderTrackingResponseDto;
import com.azneotech.ordermanagementservice.dtos.PageResponseDto;
import com.azneotech.ordermanagementservice.dtos.TrackingEventResponseDto;
import com.azneotech.ordermanagementservice.models.Order;
import com.azneotech.ordermanagementservice.models.OrderItem;
import com.azneotech.ordermanagementservice.models.OrderTrackingEvent;
import com.azneotech.ordermanagementservice.models.ShippingAddress;
import org.springframework.data.domain.Page;

/** Maps entities to response DTOs. Call it while the order's collections are still loadable (inside the service transaction). */
public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponseDto toOrderResponseDto(Order order) {
        return OrderResponseDto.builder()
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .paymentLink(order.getPaymentLink())
                .currency(order.getCurrency())
                .items(order.getItems().stream().map(OrderMapper::toOrderItemResponseDto).toList())
                .totalQuantity(order.getTotalQuantity())
                .totalAmount(order.getTotalAmount())
                .deliveryAddress(toDeliveryAddressDto(order.getShippingAddress()))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    public static OrderSummaryResponseDto toOrderSummaryResponseDto(Order order) {
        return OrderSummaryResponseDto.builder()
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .totalQuantity(order.getTotalQuantity())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .createdAt(order.getCreatedAt())
                .build();
    }

    public static PageResponseDto<OrderSummaryResponseDto> toPageResponseDto(Page<Order> page) {
        return new PageResponseDto<>(
                page.getContent().stream().map(OrderMapper::toOrderSummaryResponseDto).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static OrderTrackingResponseDto toOrderTrackingResponseDto(Order order) {
        return OrderTrackingResponseDto.builder()
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .events(order.getTrackingEvents().stream().map(OrderMapper::toTrackingEventResponseDto).toList())
                .build();
    }

    private static TrackingEventResponseDto toTrackingEventResponseDto(OrderTrackingEvent event) {
        return TrackingEventResponseDto.builder()
                .status(event.getStatus())
                .note(event.getNote())
                .timestamp(event.getCreatedAt())
                .build();
    }

    private static OrderItemResponseDto toOrderItemResponseDto(OrderItem item) {
        return OrderItemResponseDto.builder()
                .productId(item.getProductId())
                .title(item.getTitle())
                .imageUrl(item.getImageUrl())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .lineTotal(item.getLineTotal())
                .build();
    }

    private static DeliveryAddressDto toDeliveryAddressDto(ShippingAddress address) {
        return DeliveryAddressDto.builder()
                .fullName(address.getFullName())
                .phoneNumber(address.getPhoneNumber())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .build();
    }

    public static ShippingAddress toShippingAddress(DeliveryAddressDto dto) {
        return ShippingAddress.builder()
                .fullName(dto.getFullName())
                .phoneNumber(dto.getPhoneNumber())
                .addressLine1(dto.getAddressLine1())
                .addressLine2(dto.getAddressLine2())
                .city(dto.getCity())
                .state(dto.getState())
                .postalCode(dto.getPostalCode())
                .country(dto.getCountry())
                .build();
    }

}
