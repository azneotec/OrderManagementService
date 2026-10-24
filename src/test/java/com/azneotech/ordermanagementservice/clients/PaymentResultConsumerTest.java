package com.azneotech.ordermanagementservice.clients;

import com.azneotech.ordermanagementservice.dtos.PaymentResultMessage;
import com.azneotech.ordermanagementservice.services.IOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentResultConsumerTest {

    private final IOrderService orderService = mock(IOrderService.class);
    private final PaymentResultConsumer consumer = new PaymentResultConsumer(new ObjectMapper(), orderService);

    @Test
    void passesAPaymentResultToTheOrderService() {
        consumer.onPaymentResult("""
                {"orderNumber":"ORD-1","status":"LINK_CREATED","paymentLink":"https://pay/1","extra":"ignored"}""");

        verify(orderService).handlePaymentResult(
                new PaymentResultMessage("ORD-1", PaymentResultMessage.Status.LINK_CREATED, "https://pay/1", null));
    }

    @Test
    void dropsMessagesItCannotUseInsteadOfFailing() {
        consumer.onPaymentResult("not json");
        consumer.onPaymentResult("{\"orderNumber\":\"ORD-1\",\"status\":\"REFUNDED\"}");
        consumer.onPaymentResult("{\"status\":\"PAID\"}");
        consumer.onPaymentResult("{\"orderNumber\":\"ORD-1\"}");

        verifyNoInteractions(orderService);
    }

    @Test
    void listensToThePaymentResultTopicProperty() throws NoSuchMethodException {
        KafkaListener listener = PaymentResultConsumer.class.getMethod("onPaymentResult", String.class)
                .getAnnotation(KafkaListener.class);

        assertThat(listener.topics()).containsExactly("${orders.topics.payment-result}");
    }

}
