package com.azneotech.ordermanagementservice.services;

import com.azneotech.ordermanagementservice.clients.KafkaProducerClient;
import com.azneotech.ordermanagementservice.dtos.EmailDto;
import com.azneotech.ordermanagementservice.dtos.PaymentRequestedMessage;
import com.azneotech.ordermanagementservice.models.Order;
import com.azneotech.ordermanagementservice.models.OrderItem;
import com.azneotech.ordermanagementservice.models.PaymentStatus;
import com.azneotech.ordermanagementservice.models.ShippingAddress;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Everything OrderManagementService says on Kafka: payment requests for PaymentService and customer emails for
 * NotificationService. All of it is best-effort and goes out only after the surrounding transaction commits.
 */
@Slf4j
@Component
public class OrderEventPublisher {

    private final KafkaProducerClient kafkaProducerClient;
    private final ObjectMapper objectMapper;
    private final String paymentRequestedTopic;
    private final String orderNotificationTopic;
    private final String sender;

    public OrderEventPublisher(KafkaProducerClient kafkaProducerClient,
                               ObjectMapper objectMapper,
                               @Value("${orders.topics.payment-requested}") String paymentRequestedTopic,
                               @Value("${orders.topics.order-notification}") String orderNotificationTopic,
                               @Value("${orders.notification.sender}") String sender) {
        this.kafkaProducerClient = kafkaProducerClient;
        this.objectMapper = objectMapper;
        this.paymentRequestedTopic = paymentRequestedTopic;
        this.orderNotificationTopic = orderNotificationTopic;
        this.sender = sender;
    }

    /** Asks PaymentService for a payment link for this order. */
    public void requestPayment(Order order) {
        ShippingAddress address = order.getShippingAddress();
        send(paymentRequestedTopic, order.getOrderNumber(), new PaymentRequestedMessage(
                order.getOrderNumber(),
                order.getTotalAmount().movePointRight(2).longValueExact(),
                order.getCurrency(),
                address.getFullName(),
                order.getCustomerEmail(),
                address.getPhoneNumber()));
    }

    /** Emails the customer about the order's current status; statuses that need no email are skipped. */
    public void notifyStatus(Order order) {
        String name = order.getShippingAddress().getFullName();
        String number = order.getOrderNumber();
        switch (order.getStatus()) {
            case CONFIRMED -> email(order, "Your order " + number + " is confirmed", confirmationBody(order));
            case PAYMENT_FAILED -> email(order, "Payment failed for order " + number,
                    "Hi " + name + ",\n\nWe could not collect the payment for order " + number
                            + ", so the order was not placed. You have not been charged for it.\n"
                            + "You can place the order again from your cart.");
            case SHIPPED -> email(order, "Your order " + number + " has shipped",
                    "Hi " + name + ",\n\nGood news: order " + number + " is on its way.");
            case OUT_FOR_DELIVERY -> email(order, "Your order " + number + " is out for delivery",
                    "Hi " + name + ",\n\nOrder " + number + " is out for delivery and should reach you today.");
            case DELIVERED -> email(order, "Your order " + number + " was delivered",
                    "Hi " + name + ",\n\nOrder " + number + " was delivered. Thank you for shopping with us.");
            case CANCELLED -> email(order, "Your order " + number + " was cancelled",
                    "Hi " + name + ",\n\nOrder " + number + " was cancelled."
                            + (order.getPaymentStatus() == PaymentStatus.PAID
                            ? " The payment you made will be refunded." : ""));
            case PENDING_PAYMENT, PROCESSING -> {
                // Nothing to tell the customer yet.
            }
        }
    }

    /** Emails the customer the link to pay for a pending order. */
    public void notifyPaymentLink(Order order) {
        email(order, "Complete the payment for order " + order.getOrderNumber(),
                "Hi " + order.getShippingAddress().getFullName() + ",\n\nYour order " + order.getOrderNumber()
                        + " is waiting for payment of " + order.getCurrency() + " " + order.getTotalAmount()
                        + ". Pay here to confirm it:\n" + order.getPaymentLink());
    }

    private String confirmationBody(Order order) {
        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(order.getShippingAddress().getFullName()).append(",\n\n")
                .append("Thank you for your order. Order ").append(order.getOrderNumber()).append(" is confirmed.\n\n")
                .append("Items:\n");
        for (OrderItem item : order.getItems()) {
            body.append("- ").append(item.getTitle()).append(" x ").append(item.getQuantity())
                    .append(" @ ").append(item.getUnitPrice()).append(" = ").append(item.getLineTotal()).append('\n');
        }
        ShippingAddress address = order.getShippingAddress();
        body.append("\nTotal: ").append(order.getCurrency()).append(' ').append(order.getTotalAmount()).append('\n')
                .append("Payment: ").append(order.getPaymentStatus() == PaymentStatus.PAID
                        ? "paid online" : "pay on delivery").append("\n\n")
                .append("Delivering to: ").append(address.getFullName()).append(", ").append(address.getAddressLine1());
        if (address.getAddressLine2() != null && !address.getAddressLine2().isBlank()) {
            body.append(", ").append(address.getAddressLine2());
        }
        body.append(", ").append(address.getCity()).append(", ").append(address.getState()).append(' ')
                .append(address.getPostalCode()).append(", ").append(address.getCountry()).append("\n\n")
                .append("Quote order number ").append(order.getOrderNumber()).append(" to track your delivery.");
        return body.toString();
    }

    private void email(Order order, String subject, String body) {
        send(orderNotificationTopic, order.getOrderNumber(),
                new EmailDto(order.getCustomerEmail(), sender, subject, body));
    }

    private void send(String topic, String key, Object message) {
        try {
            kafkaProducerClient.sendAfterCommit(topic, key, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            // Publishing is best-effort and must not undo the order change that triggered it.
            log.error("Could not serialize message for {} on topic '{}'", key, topic, e);
        }
    }

}
