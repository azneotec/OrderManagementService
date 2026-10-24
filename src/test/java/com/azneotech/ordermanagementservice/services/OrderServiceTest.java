package com.azneotech.ordermanagementservice.services;

import com.azneotech.ordermanagementservice.clients.IProductCatalogClient;
import com.azneotech.ordermanagementservice.dtos.DeliveryAddressDto;
import com.azneotech.ordermanagementservice.dtos.OrderItemRequestDto;
import com.azneotech.ordermanagementservice.dtos.OrderResponseDto;
import com.azneotech.ordermanagementservice.dtos.OrderTrackingResponseDto;
import com.azneotech.ordermanagementservice.dtos.PaymentResultMessage;
import com.azneotech.ordermanagementservice.dtos.PlaceOrderRequestDto;
import com.azneotech.ordermanagementservice.dtos.ProductCatalogDto;
import com.azneotech.ordermanagementservice.exceptions.DownstreamUnavailableException;
import com.azneotech.ordermanagementservice.exceptions.InvalidOrderRequestException;
import com.azneotech.ordermanagementservice.exceptions.OrderConflictException;
import com.azneotech.ordermanagementservice.exceptions.OrderNotFoundException;
import com.azneotech.ordermanagementservice.exceptions.ProductNotFoundException;
import com.azneotech.ordermanagementservice.models.Order;
import com.azneotech.ordermanagementservice.models.OrderItem;
import com.azneotech.ordermanagementservice.models.OrderStatus;
import com.azneotech.ordermanagementservice.models.PaymentMethod;
import com.azneotech.ordermanagementservice.models.PaymentStatus;
import com.azneotech.ordermanagementservice.models.ShippingAddress;
import com.azneotech.ordermanagementservice.repos.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock IProductCatalogClient catalogClient;
    @Mock OrderEventPublisher eventPublisher;

    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, catalogClient, eventPublisher);
    }

    private static ProductCatalogDto product(long id, String title, String price) {
        return new ProductCatalogDto(id, title, new BigDecimal(price), "http://img/" + id);
    }

    private static DeliveryAddressDto address() {
        return DeliveryAddressDto.builder().fullName("Alice").phoneNumber("+15551234567").addressLine1("1 Main St")
                .city("Pune").state("MH").postalCode("411001").country("IN").build();
    }

    private static PlaceOrderRequestDto request(PaymentMethod method, BigDecimal expectedTotal, OrderItemRequestDto... items) {
        return PlaceOrderRequestDto.builder().items(List.of(items)).deliveryAddress(address())
                .paymentMethod(method).expectedTotal(expectedTotal).build();
    }

    private static Order order(OrderStatus status, PaymentMethod method, PaymentStatus paymentStatus) {
        Order order = new Order();
        order.setOrderNumber("ORD-20261024-ABCDEFGH");
        order.setUserId(7L);
        order.setCustomerEmail("alice@example.com");
        order.setPaymentMethod(method);
        order.setPaymentStatus(paymentStatus);
        order.setCurrency("INR");
        order.setTotalQuantity(1);
        order.setTotalAmount(new BigDecimal("10.00"));
        order.setShippingAddress(ShippingAddress.builder().fullName("Alice").phoneNumber("1").addressLine1("a")
                .city("c").state("s").postalCode("p").country("IN").build());
        order.addItem(new OrderItem(5L, "Backpack", null, new BigDecimal("10.00"), 1));
        order.moveTo(status, "created");
        return order;
    }

    // ---- placeOrder

    @Test
    void placeOrder_pricesFromTheCatalogAndWaitsForOnlinePayment() {
        when(catalogClient.getProduct(5L)).thenReturn(product(5, "Backpack", "109.95"));
        when(catalogClient.getProduct(6L)).thenReturn(product(6, "Shirt", "22.30"));
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

        OrderResponseDto response = service.placeOrder(7L, "alice@example.com", request(PaymentMethod.UPI,
                new BigDecimal("242.20"), new OrderItemRequestDto(5L, 2), new OrderItemRequestDto(6L, 1)));

        assertThat(response.getOrderNumber()).matches("ORD-\\d{8}-[A-Z2-9]{8}");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("242.20");
        assertThat(response.getTotalQuantity()).isEqualTo(3);
        assertThat(response.getCurrency()).isEqualTo("INR");
        assertThat(response.getItems()).extracting("productId", "unitPrice", "lineTotal")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(5L, new BigDecimal("109.95"), new BigDecimal("219.90")),
                        org.assertj.core.groups.Tuple.tuple(6L, new BigDecimal("22.30"), new BigDecimal("22.30")));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(7L);
        assertThat(saved.getValue().getCustomerEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getTrackingEvents()).extracting(e -> e.getStatus()).containsExactly(OrderStatus.PENDING_PAYMENT);
        verify(eventPublisher).requestPayment(saved.getValue());
        verifyNoMoreInteractions(eventPublisher);
    }

    @Test
    void placeOrder_confirmsCashOnDeliveryAtOnceAndSendsTheConfirmation() {
        when(catalogClient.getProduct(5L)).thenReturn(product(5, "Backpack", "10.00"));
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

        OrderResponseDto response = service.placeOrder(7L, "alice@example.com",
                request(PaymentMethod.COD, null, new OrderItemRequestDto(5L, 1)));

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAY_ON_DELIVERY);
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(eventPublisher).notifyStatus(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(eventPublisher, never()).requestPayment(any());
    }

    @Test
    void placeOrder_ignoresAnyPriceTheCallerCouldHaveSent() {
        // The request has no price field at all; the total comes from the catalog.
        when(catalogClient.getProduct(5L)).thenReturn(product(5, "Backpack", "10.004"));
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

        OrderResponseDto response = service.placeOrder(7L, "a@b.c",
                request(PaymentMethod.COD, null, new OrderItemRequestDto(5L, 3)));

        assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo("10.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void placeOrder_refusesWhenThePriceDiffersFromTheQuotedTotal() {
        when(catalogClient.getProduct(5L)).thenReturn(product(5, "Backpack", "12.00"));

        assertThatThrownBy(() -> service.placeOrder(7L, "a@b.c",
                request(PaymentMethod.UPI, new BigDecimal("10.00"), new OrderItemRequestDto(5L, 1))))
                .isInstanceOf(OrderConflictException.class)
                .hasMessageContaining("Prices have changed");
        verifyNoInteractions(orderRepository, eventPublisher);
    }

    @Test
    void placeOrder_listsEveryProductTheCatalogNoLongerHas() {
        when(catalogClient.getProduct(5L)).thenReturn(product(5, "Backpack", "10.00"));
        when(catalogClient.getProduct(8L)).thenThrow(new ProductNotFoundException(8L));
        when(catalogClient.getProduct(9L)).thenThrow(new ProductNotFoundException(9L));

        assertThatThrownBy(() -> service.placeOrder(7L, "a@b.c", request(PaymentMethod.COD, null,
                new OrderItemRequestDto(5L, 1), new OrderItemRequestDto(8L, 1), new OrderItemRequestDto(9L, 1))))
                .isInstanceOf(OrderConflictException.class)
                .hasMessageContaining("[8, 9]");
        verifyNoInteractions(orderRepository, eventPublisher);
    }

    @Test
    void placeOrder_rejectsTheSameProductTwice() {
        assertThatThrownBy(() -> service.placeOrder(7L, "a@b.c", request(PaymentMethod.COD, null,
                new OrderItemRequestDto(5L, 1), new OrderItemRequestDto(5L, 2))))
                .isInstanceOf(InvalidOrderRequestException.class);
        verifyNoInteractions(catalogClient, orderRepository);
    }

    @Test
    void placeOrder_propagatesACatalogOutageWithoutSavingAnything() {
        when(catalogClient.getProduct(5L)).thenThrow(new DownstreamUnavailableException("product-catalog-service"));

        assertThatThrownBy(() -> service.placeOrder(7L, "a@b.c",
                request(PaymentMethod.COD, null, new OrderItemRequestDto(5L, 1))))
                .isInstanceOf(DownstreamUnavailableException.class);
        verifyNoInteractions(orderRepository, eventPublisher);
    }

    // ---- history and lookup

    @Test
    void getOrderHistory_isNewestFirstAndClampsThePageSize() {
        Order order = order(OrderStatus.CONFIRMED, PaymentMethod.COD, PaymentStatus.PAY_ON_DELIVERY);
        when(orderRepository.findByUserId(any(Long.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 50), 1));

        var page = service.getOrderHistory(7L, -3, 1000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByUserId(any(Long.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().toString()).isEqualTo("createdAt: DESC,id: DESC");
        assertThat(page.getContent()).extracting("orderNumber").containsExactly("ORD-20261024-ABCDEFGH");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getOrder_isScopedToTheCaller() {
        when(orderRepository.findByOrderNumberAndUserId("ORD-X", 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(7L, "ORD-X")).isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> service.getTracking(7L, "ORD-X")).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void getTracking_returnsTheTimelineOldestFirst() {
        Order order = order(OrderStatus.PENDING_PAYMENT, PaymentMethod.UPI, PaymentStatus.PENDING);
        order.moveTo(OrderStatus.CONFIRMED, "Payment received");
        when(orderRepository.findByOrderNumberAndUserId(order.getOrderNumber(), 7L)).thenReturn(Optional.of(order));

        OrderTrackingResponseDto tracking = service.getTracking(7L, order.getOrderNumber());

        assertThat(tracking.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(tracking.getEvents()).extracting("status")
                .containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED);
    }

    // ---- admin status changes

    @Test
    void updateStatus_movesTheOrderLogsItAndNotifiesTheCustomer() {
        Order order = order(OrderStatus.PROCESSING, PaymentMethod.COD, PaymentStatus.PAY_ON_DELIVERY);
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));

        OrderTrackingResponseDto tracking = service.updateStatus(order.getOrderNumber(), OrderStatus.SHIPPED, "Bluedart 123");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(tracking.getEvents()).extracting("status", "note")
                .contains(org.assertj.core.groups.Tuple.tuple(OrderStatus.SHIPPED, "Bluedart 123"));
        verify(eventPublisher).notifyStatus(order);
    }

    @Test
    void updateStatus_refusesAMoveThatSkipsAStep() {
        Order order = order(OrderStatus.CONFIRMED, PaymentMethod.COD, PaymentStatus.PAY_ON_DELIVERY);
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateStatus(order.getOrderNumber(), OrderStatus.DELIVERED, null))
                .isInstanceOf(OrderConflictException.class)
                .hasMessageContaining("CONFIRMED").hasMessageContaining("DELIVERED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateStatus_unknownOrderIsNotFound() {
        when(orderRepository.findByOrderNumber("ORD-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus("ORD-X", OrderStatus.PROCESSING, null))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ---- payment results

    private static PaymentResultMessage result(PaymentResultMessage.Status status, String link, String message) {
        return new PaymentResultMessage("ORD-20261024-ABCDEFGH", status, link, message);
    }

    @Test
    void paymentResult_paidConfirmsThePendingOrderAndNotifies() {
        Order order = order(OrderStatus.PENDING_PAYMENT, PaymentMethod.UPI, PaymentStatus.PENDING);
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));

        service.handlePaymentResult(result(PaymentResultMessage.Status.PAID, null, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getTrackingEvents()).extracting("status", "note")
                .contains(org.assertj.core.groups.Tuple.tuple(OrderStatus.CONFIRMED, "Payment received"));
        verify(eventPublisher).notifyStatus(order);
    }

    @Test
    void paymentResult_paidTwiceConfirmsOnce() {
        Order order = order(OrderStatus.PENDING_PAYMENT, PaymentMethod.UPI, PaymentStatus.PENDING);
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));

        service.handlePaymentResult(result(PaymentResultMessage.Status.PAID, null, null));
        service.handlePaymentResult(result(PaymentResultMessage.Status.PAID, null, null));

        assertThat(order.getTrackingEvents()).hasSize(2);
        verify(eventPublisher).notifyStatus(order);
    }

    @Test
    void paymentResult_paidAfterCancellationRecordsThePaymentButKeepsTheOrderCancelled() {
        Order order = order(OrderStatus.CANCELLED, PaymentMethod.UPI, PaymentStatus.PENDING);
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));

        service.handlePaymentResult(result(PaymentResultMessage.Status.PAID, null, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void paymentResult_failedMarksThePendingOrderAndNotifies() {
        Order order = order(OrderStatus.PENDING_PAYMENT, PaymentMethod.CARD, PaymentStatus.PENDING);
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));

        service.handlePaymentResult(result(PaymentResultMessage.Status.FAILED, null, "Link expired"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getTrackingEvents()).extracting("note").contains("Link expired");
        verify(eventPublisher).notifyStatus(order);
    }

    @Test
    void paymentResult_failedNeverUndoesAPaidOrder() {
        Order order = order(OrderStatus.CONFIRMED, PaymentMethod.CARD, PaymentStatus.PAID);
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));

        service.handlePaymentResult(result(PaymentResultMessage.Status.FAILED, null, "late"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void paymentResult_linkCreatedStoresTheLinkOnceAndEmailsIt() {
        Order order = order(OrderStatus.PENDING_PAYMENT, PaymentMethod.UPI, PaymentStatus.PENDING);
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));

        service.handlePaymentResult(result(PaymentResultMessage.Status.LINK_CREATED, "https://pay/1", null));
        service.handlePaymentResult(result(PaymentResultMessage.Status.LINK_CREATED, "https://pay/1", null));

        assertThat(order.getPaymentLink()).isEqualTo("https://pay/1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(eventPublisher).notifyPaymentLink(order);
    }

    @Test
    void paymentResult_forAnUnknownOrderIsIgnored() {
        when(orderRepository.findByOrderNumber("ORD-20261024-ABCDEFGH")).thenReturn(Optional.empty());

        service.handlePaymentResult(result(PaymentResultMessage.Status.PAID, null, null));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void paymentResult_forACashOnDeliveryOrderIsIgnored() {
        Order order = order(OrderStatus.CONFIRMED, PaymentMethod.COD, PaymentStatus.PAY_ON_DELIVERY);
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));

        service.handlePaymentResult(result(PaymentResultMessage.Status.FAILED, null, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verifyNoInteractions(eventPublisher);
    }

}
