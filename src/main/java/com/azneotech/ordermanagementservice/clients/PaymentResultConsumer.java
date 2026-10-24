package com.azneotech.ordermanagementservice.clients;

import com.azneotech.ordermanagementservice.dtos.PaymentResultMessage;
import com.azneotech.ordermanagementservice.services.IOrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Applies the results PaymentService publishes on {@code payment-result} to their orders. A message that cannot
 * be understood is logged and dropped: retrying it would never help and would block the partition behind it.
 */
@Slf4j
@Component
public class PaymentResultConsumer {

    private final ObjectMapper objectMapper;
    private final IOrderService orderService;

    public PaymentResultConsumer(ObjectMapper objectMapper, IOrderService orderService) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
    }

    @KafkaListener(topics = "${orders.topics.payment-result}")
    public void onPaymentResult(String message) {
        PaymentResultMessage result;
        try {
            result = objectMapper.readValue(message, PaymentResultMessage.class);
        } catch (JsonProcessingException e) {
            log.error("Ignoring malformed payment-result message: {}", message, e);
            return;
        }
        if (result.orderNumber() == null || result.status() == null) {
            log.error("Ignoring payment-result message without an order number or status: {}", message);
            return;
        }
        orderService.handlePaymentResult(result);
    }

}
