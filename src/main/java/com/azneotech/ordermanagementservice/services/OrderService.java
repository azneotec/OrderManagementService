package com.azneotech.ordermanagementservice.services;

import com.azneotech.ordermanagementservice.clients.IProductCatalogClient;
import com.azneotech.ordermanagementservice.dtos.OrderItemRequestDto;
import com.azneotech.ordermanagementservice.dtos.OrderResponseDto;
import com.azneotech.ordermanagementservice.dtos.OrderSummaryResponseDto;
import com.azneotech.ordermanagementservice.dtos.OrderTrackingResponseDto;
import com.azneotech.ordermanagementservice.dtos.PageResponseDto;
import com.azneotech.ordermanagementservice.dtos.PaymentResultMessage;
import com.azneotech.ordermanagementservice.dtos.PlaceOrderRequestDto;
import com.azneotech.ordermanagementservice.dtos.ProductCatalogDto;
import com.azneotech.ordermanagementservice.exceptions.InvalidOrderRequestException;
import com.azneotech.ordermanagementservice.exceptions.OrderConflictException;
import com.azneotech.ordermanagementservice.exceptions.OrderNotFoundException;
import com.azneotech.ordermanagementservice.exceptions.ProductNotFoundException;
import com.azneotech.ordermanagementservice.mappers.OrderMapper;
import com.azneotech.ordermanagementservice.models.Order;
import com.azneotech.ordermanagementservice.models.OrderItem;
import com.azneotech.ordermanagementservice.models.OrderStatus;
import com.azneotech.ordermanagementservice.models.PaymentMethod;
import com.azneotech.ordermanagementservice.models.PaymentStatus;
import com.azneotech.ordermanagementservice.repos.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class OrderService implements IOrderService {

    /** The catalog has no currency of its own; PaymentService collects in INR. */
    static final String CURRENCY = "INR";
    private static final int PRICE_SCALE = 2;
    static final int MAX_PAGE_SIZE = 50;
    /** No 0, O, 1, I or L: order numbers get read out over the phone. */
    private static final String ORDER_NUMBER_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int ORDER_NUMBER_RANDOM_LENGTH = 8;
    private static final DateTimeFormatter ORDER_NUMBER_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final OrderRepository orderRepository;
    private final IProductCatalogClient catalogClient;
    private final OrderEventPublisher eventPublisher;
    private final SecureRandom random = new SecureRandom();

    public OrderService(OrderRepository orderRepository, IProductCatalogClient catalogClient, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.catalogClient = catalogClient;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Deliberately not {@code @Transactional}: the catalog calls are HTTP and must not hold a database
     * transaction open. The single {@code save} is its own transaction, and events are published after it.
     */
    @Override
    public OrderResponseDto placeOrder(Long userId, String customerEmail, PlaceOrderRequestDto request) {
        requireDistinctProducts(request.getItems());

        List<OrderItem> items = new ArrayList<>();
        List<Long> unavailable = new ArrayList<>();
        for (OrderItemRequestDto line : request.getItems()) {
            try {
                ProductCatalogDto product = catalogClient.getProduct(line.getProductId());
                items.add(new OrderItem(line.getProductId(), product.getTitle(), product.getImage(),
                        toPrice(product.getPrice()), line.getQuantity()));
            } catch (ProductNotFoundException e) {
                unavailable.add(line.getProductId());
            }
        }
        if (!unavailable.isEmpty()) {
            throw new OrderConflictException("These products are no longer available: " + unavailable);
        }

        BigDecimal total = items.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.getExpectedTotal() != null && request.getExpectedTotal().compareTo(total) != 0) {
            throw new OrderConflictException("Prices have changed since you reviewed your order, please review it again");
        }

        boolean cashOnDelivery = request.getPaymentMethod() == PaymentMethod.COD;
        Order order = new Order();
        order.setOrderNumber(newOrderNumber());
        order.setUserId(userId);
        order.setCustomerEmail(customerEmail);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setPaymentStatus(cashOnDelivery ? PaymentStatus.PAY_ON_DELIVERY : PaymentStatus.PENDING);
        order.setCurrency(CURRENCY);
        order.setTotalQuantity(items.stream().mapToInt(OrderItem::getQuantity).sum());
        order.setTotalAmount(total);
        order.setShippingAddress(OrderMapper.toShippingAddress(request.getDeliveryAddress()));
        items.forEach(order::addItem);
        order.moveTo(cashOnDelivery ? OrderStatus.CONFIRMED : OrderStatus.PENDING_PAYMENT,
                cashOnDelivery ? "Order placed, pay on delivery" : "Order placed, waiting for payment");

        Order saved = orderRepository.save(order);

        if (cashOnDelivery) {
            eventPublisher.notifyStatus(saved);
        } else {
            eventPublisher.requestPayment(saved);
        }
        return OrderMapper.toOrderResponseDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<OrderSummaryResponseDto> getOrderHistory(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return OrderMapper.toPageResponseDto(orderRepository.findByUserId(userId, pageRequest));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDto getOrder(Long userId, String orderNumber) {
        return OrderMapper.toOrderResponseDto(orderRepository.findByOrderNumberAndUserId(orderNumber, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderNumber)));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderTrackingResponseDto getTracking(Long userId, String orderNumber) {
        return OrderMapper.toOrderTrackingResponseDto(orderRepository.findByOrderNumberAndUserId(orderNumber, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderNumber)));
    }

    @Override
    @Transactional
    public OrderTrackingResponseDto updateStatus(String orderNumber, OrderStatus newStatus, String note) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException(orderNumber));
        if (!order.getStatus().canBeChangedByAdminTo(newStatus)) {
            throw new OrderConflictException("Order " + orderNumber + " cannot move from " + order.getStatus() + " to " + newStatus);
        }
        if (newStatus == OrderStatus.CANCELLED && order.getPaymentStatus() == PaymentStatus.PAID) {
            log.warn("Order {} was cancelled after payment, the customer needs a refund", orderNumber);
        }
        order.moveTo(newStatus, note);
        eventPublisher.notifyStatus(order);
        return OrderMapper.toOrderTrackingResponseDto(order);
    }

    @Override
    @Transactional
    public void handlePaymentResult(PaymentResultMessage result) {
        Order order = orderRepository.findByOrderNumber(result.orderNumber()).orElse(null);
        if (order == null) {
            log.warn("Ignoring {} payment result for unknown order {}", result.status(), result.orderNumber());
            return;
        }
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            log.warn("Ignoring {} payment result for cash-on-delivery order {}", result.status(), result.orderNumber());
            return;
        }
        switch (result.status()) {
            case LINK_CREATED -> applyPaymentLink(order, result.paymentLink());
            case PAID -> applyPaid(order);
            case FAILED -> applyFailed(order, result.message());
        }
    }

    private void applyPaymentLink(Order order, String paymentLink) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || paymentLink == null || paymentLink.equals(order.getPaymentLink())) {
            return;
        }
        order.setPaymentLink(paymentLink);
        eventPublisher.notifyPaymentLink(order);
    }

    private void applyPaid(Order order) {
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return;
        }
        order.setPaymentStatus(PaymentStatus.PAID);
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT || order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            order.moveTo(OrderStatus.CONFIRMED, "Payment received");
            eventPublisher.notifyStatus(order);
        } else {
            // Paid after the order was cancelled: the money is real, so record it, and flag it for a refund.
            log.error("Payment received for order {} that is {}, the customer needs a refund", order.getOrderNumber(), order.getStatus());
        }
    }

    private void applyFailed(Order order, String reason) {
        if (order.getPaymentStatus() == PaymentStatus.PAID || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        order.setPaymentStatus(PaymentStatus.FAILED);
        order.moveTo(OrderStatus.PAYMENT_FAILED, reason != null && !reason.isBlank() ? truncate(reason) : "Payment failed");
        eventPublisher.notifyStatus(order);
    }

    private static void requireDistinctProducts(List<OrderItemRequestDto> lines) {
        Set<Long> seen = new HashSet<>();
        for (OrderItemRequestDto line : lines) {
            if (!seen.add(line.getProductId())) {
                throw new InvalidOrderRequestException("Product " + line.getProductId() + " is listed more than once");
            }
        }
    }

    private String newOrderNumber() {
        StringBuilder number = new StringBuilder("ORD-")
                .append(ORDER_NUMBER_DATE.format(LocalDate.now(ZoneOffset.UTC))).append('-');
        for (int i = 0; i < ORDER_NUMBER_RANDOM_LENGTH; i++) {
            number.append(ORDER_NUMBER_ALPHABET.charAt(random.nextInt(ORDER_NUMBER_ALPHABET.length())));
        }
        return number.toString();
    }

    private static BigDecimal toPrice(BigDecimal price) {
        return price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private static String truncate(String note) {
        return note.length() <= 255 ? note : note.substring(0, 255);
    }

}
