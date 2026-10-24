package com.azneotech.ordermanagementservice.services;

import com.azneotech.ordermanagementservice.clients.KafkaProducerClient;
import com.azneotech.ordermanagementservice.dtos.EmailDto;
import com.azneotech.ordermanagementservice.models.Order;
import com.azneotech.ordermanagementservice.models.OrderItem;
import com.azneotech.ordermanagementservice.models.OrderStatus;
import com.azneotech.ordermanagementservice.models.PaymentMethod;
import com.azneotech.ordermanagementservice.models.PaymentStatus;
import com.azneotech.ordermanagementservice.models.ShippingAddress;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock KafkaProducerClient kafka;
    OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(kafka, objectMapper, "payment-requested", "order-notification", "shop@example.com");
    }

    private static Order order(OrderStatus status, PaymentStatus paymentStatus) {
        Order order = new Order();
        order.setOrderNumber("ORD-20261024-ABCDEFGH");
        order.setCustomerEmail("alice@example.com");
        order.setPaymentMethod(paymentStatus == PaymentStatus.PAY_ON_DELIVERY ? PaymentMethod.COD : PaymentMethod.UPI);
        order.setPaymentStatus(paymentStatus);
        order.setStatus(status);
        order.setCurrency("INR");
        order.setTotalQuantity(3);
        order.setTotalAmount(new BigDecimal("242.20"));
        order.setPaymentLink("https://pay/1");
        order.setShippingAddress(ShippingAddress.builder().fullName("Alice").phoneNumber("+15551234567")
                .addressLine1("1 Main St").addressLine2("Flat 4").city("Pune").state("MH").postalCode("411001").country("IN").build());
        order.addItem(new OrderItem(5L, "Backpack", null, new BigDecimal("109.95"), 2));
        order.addItem(new OrderItem(6L, "Shirt", null, new BigDecimal("22.30"), 1));
        return order;
    }

    private EmailDto sentEmail() throws Exception {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafka).sendAfterCommit(eq("order-notification"), eq("ORD-20261024-ABCDEFGH"), message.capture());
        return objectMapper.readValue(message.getValue(), EmailDto.class);
    }

    @Test
    void requestPayment_sendsTheAmountInMinorUnitsKeyedByTheOrderNumber() throws Exception {
        publisher.requestPayment(order(OrderStatus.PENDING_PAYMENT, PaymentStatus.PENDING));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafka).sendAfterCommit(eq("payment-requested"), eq("ORD-20261024-ABCDEFGH"), message.capture());
        JsonNode json = objectMapper.readTree(message.getValue());
        assertThat(json.get("orderNumber").asText()).isEqualTo("ORD-20261024-ABCDEFGH");
        assertThat(json.get("amountMinor").asLong()).isEqualTo(24220L);
        assertThat(json.get("currency").asText()).isEqualTo("INR");
        assertThat(json.get("customerName").asText()).isEqualTo("Alice");
        assertThat(json.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(json.get("phoneNumber").asText()).isEqualTo("+15551234567");
    }

    @Test
    void confirmation_listsTheItemsTotalAddressAndPayment() throws Exception {
        publisher.notifyStatus(order(OrderStatus.CONFIRMED, PaymentStatus.PAY_ON_DELIVERY));

        EmailDto email = sentEmail();
        assertThat(email.getTo()).isEqualTo("alice@example.com");
        assertThat(email.getFrom()).isEqualTo("shop@example.com");
        assertThat(email.getSubject()).isEqualTo("Your order ORD-20261024-ABCDEFGH is confirmed");
        assertThat(email.getBody())
                .contains("Backpack x 2 @ 109.95 = ")
                .contains("Shirt x 1 @ 22.30")
                .contains("Total: INR 242.20")
                .contains("pay on delivery")
                .contains("1 Main St, Flat 4, Pune, MH 411001, IN");
    }

    @Test
    void confirmation_saysPaidOnlineForAPaidOrder() throws Exception {
        publisher.notifyStatus(order(OrderStatus.CONFIRMED, PaymentStatus.PAID));

        assertThat(sentEmail().getBody()).contains("paid online");
    }

    @Test
    void shippingProgressAndFailuresEachGetAnEmail() throws Exception {
        for (OrderStatus status : new OrderStatus[]{OrderStatus.PAYMENT_FAILED, OrderStatus.SHIPPED,
                OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.CANCELLED}) {
            org.mockito.Mockito.clearInvocations(kafka);
            publisher.notifyStatus(order(status, PaymentStatus.PENDING));
            assertThat(sentEmail().getSubject()).contains("ORD-20261024-ABCDEFGH");
        }
    }

    @Test
    void cancellingAPaidOrderPromisesARefund() throws Exception {
        publisher.notifyStatus(order(OrderStatus.CANCELLED, PaymentStatus.PAID));

        assertThat(sentEmail().getBody()).contains("refunded");
    }

    @Test
    void quietStatusesSendNothing() {
        publisher.notifyStatus(order(OrderStatus.PENDING_PAYMENT, PaymentStatus.PENDING));
        publisher.notifyStatus(order(OrderStatus.PROCESSING, PaymentStatus.PAID));

        verify(kafka, never()).sendAfterCommit(any(), any(), any());
    }

    @Test
    void paymentLinkEmailCarriesTheLinkAndAmount() throws Exception {
        publisher.notifyPaymentLink(order(OrderStatus.PENDING_PAYMENT, PaymentStatus.PENDING));

        EmailDto email = sentEmail();
        assertThat(email.getSubject()).contains("Complete the payment");
        assertThat(email.getBody()).contains("https://pay/1").contains("INR 242.20");
    }

}
