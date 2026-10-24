package com.azneotech.ordermanagementservice.services;

import com.azneotech.ordermanagementservice.clients.IAuthClient;
import com.azneotech.ordermanagementservice.clients.IProductCatalogClient;
import com.azneotech.ordermanagementservice.clients.KafkaProducerClient;
import com.azneotech.ordermanagementservice.dtos.DeliveryAddressDto;
import com.azneotech.ordermanagementservice.dtos.OrderItemRequestDto;
import com.azneotech.ordermanagementservice.dtos.OrderResponseDto;
import com.azneotech.ordermanagementservice.dtos.PaymentResultMessage;
import com.azneotech.ordermanagementservice.dtos.PlaceOrderRequestDto;
import com.azneotech.ordermanagementservice.dtos.ProductCatalogDto;
import com.azneotech.ordermanagementservice.exceptions.OrderNotFoundException;
import com.azneotech.ordermanagementservice.models.OrderStatus;
import com.azneotech.ordermanagementservice.models.PaymentMethod;
import com.azneotech.ordermanagementservice.models.PaymentStatus;
import com.azneotech.ordermanagementservice.repos.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The order lifecycle against a real MySQL (Flyway schema, JPA mapping, lazy loading, optimistic locking).
 * Uses its own database so it never touches the one the service runs on. Catalog, auth and Kafka are mocked.
 * Run with: {@code ./mvnw test -DexcludedGroups=none -Dgroups=integration}
 */
@Tag("integration")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3307/order_management_service_test?createDatabaseIfNotExist=true",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false"})
class OrderFlowIntegrationTest {

    @Autowired IOrderService orderService;
    @Autowired OrderRepository orderRepository;
    @MockBean IProductCatalogClient catalogClient;
    @MockBean IAuthClient authClient;
    @MockBean KafkaProducerClient kafka;

    @BeforeEach
    void clean() {
        orderRepository.deleteAll();
        when(catalogClient.getProduct(5L)).thenReturn(new ProductCatalogDto(5L, "Backpack", new BigDecimal("109.95"), "http://img/5"));
        when(catalogClient.getProduct(6L)).thenReturn(new ProductCatalogDto(6L, "Shirt", new BigDecimal("22.30"), null));
    }

    private static PlaceOrderRequestDto request(PaymentMethod method) {
        return PlaceOrderRequestDto.builder()
                .items(List.of(new OrderItemRequestDto(5L, 2), new OrderItemRequestDto(6L, 1)))
                .deliveryAddress(DeliveryAddressDto.builder().fullName("Alice").phoneNumber("+15551234567")
                        .addressLine1("1 Main St").city("Pune").state("MH").postalCode("411001").country("IN").build())
                .paymentMethod(method).expectedTotal(new BigDecimal("242.20")).build();
    }

    @Test
    void onlineOrderIsPlacedPaidAndTrackedThroughDelivery() {
        OrderResponseDto placed = orderService.placeOrder(7L, "alice@example.com", request(PaymentMethod.UPI));
        String number = placed.getOrderNumber();

        assertThat(placed.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(kafka).sendAfterCommit(eq("payment-requested"), eq(number), any());

        orderService.handlePaymentResult(new PaymentResultMessage(number, PaymentResultMessage.Status.LINK_CREATED, "https://pay/1", null));
        assertThat(orderService.getOrder(7L, number).getPaymentLink()).isEqualTo("https://pay/1");

        orderService.handlePaymentResult(new PaymentResultMessage(number, PaymentResultMessage.Status.PAID, null, null));
        OrderResponseDto confirmed = orderService.getOrder(7L, number);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(confirmed.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(confirmed.getTotalAmount()).isEqualByComparingTo("242.20");
        assertThat(confirmed.getItems()).extracting("title").containsExactly("Backpack", "Shirt");
        assertThat(confirmed.getDeliveryAddress().getCity()).isEqualTo("Pune");
        verify(kafka, atLeastOnce()).sendAfterCommit(eq("order-notification"), eq(number), any());

        for (OrderStatus next : List.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED)) {
            orderService.updateStatus(number, next, null);
        }
        assertThat(orderService.getTracking(7L, number).getEvents()).extracting("status").containsExactly(
                OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.SHIPPED,
                OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);
    }

    @Test
    void historyIsPagedNewestFirstAndPerUser() {
        String first = orderService.placeOrder(7L, "alice@example.com", request(PaymentMethod.COD)).getOrderNumber();
        String second = orderService.placeOrder(7L, "alice@example.com", request(PaymentMethod.COD)).getOrderNumber();
        orderService.placeOrder(8L, "bob@example.com", request(PaymentMethod.COD));

        var page = orderService.getOrderHistory(7L, 0, 1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting("orderNumber").containsExactly(second);
        assertThat(orderService.getOrderHistory(7L, 1, 1).getContent()).extracting("orderNumber").containsExactly(first);

        assertThatThrownBy(() -> orderService.getOrder(8L, first)).isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> orderService.getTracking(8L, first)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void failedPaymentEndsTheOrder() {
        String number = orderService.placeOrder(7L, "alice@example.com", request(PaymentMethod.CARD)).getOrderNumber();

        orderService.handlePaymentResult(new PaymentResultMessage(number, PaymentResultMessage.Status.FAILED, null, "Link expired"));

        OrderResponseDto order = orderService.getOrder(7L, number);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderService.getTracking(7L, number).getEvents().get(1).getNote()).isEqualTo("Link expired");
    }

}
