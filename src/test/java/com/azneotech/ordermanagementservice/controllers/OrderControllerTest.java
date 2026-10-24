package com.azneotech.ordermanagementservice.controllers;

import com.azneotech.ordermanagementservice.clients.IAuthClient;
import com.azneotech.ordermanagementservice.configs.SecurityConfig;
import com.azneotech.ordermanagementservice.dtos.DeliveryAddressDto;
import com.azneotech.ordermanagementservice.dtos.OrderItemResponseDto;
import com.azneotech.ordermanagementservice.dtos.OrderResponseDto;
import com.azneotech.ordermanagementservice.dtos.OrderSummaryResponseDto;
import com.azneotech.ordermanagementservice.dtos.OrderTrackingResponseDto;
import com.azneotech.ordermanagementservice.dtos.PageResponseDto;
import com.azneotech.ordermanagementservice.dtos.PlaceOrderRequestDto;
import com.azneotech.ordermanagementservice.dtos.TrackingEventResponseDto;
import com.azneotech.ordermanagementservice.dtos.ValidateTokenResponseDto;
import com.azneotech.ordermanagementservice.exceptions.DownstreamUnavailableException;
import com.azneotech.ordermanagementservice.exceptions.OrderConflictException;
import com.azneotech.ordermanagementservice.exceptions.OrderNotFoundException;
import com.azneotech.ordermanagementservice.models.OrderStatus;
import com.azneotech.ordermanagementservice.models.PaymentMethod;
import com.azneotech.ordermanagementservice.models.PaymentStatus;
import com.azneotech.ordermanagementservice.services.IOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the real security filter chain; only the token introspection and the order service are mocked. */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    private static final String USER_TOKEN = "Bearer user-token";
    private static final String ADMIN_TOKEN = "Bearer admin-token";
    private static final String ORDER_NUMBER = "ORD-20261024-ABCDEFGH";

    @Autowired MockMvc mockMvc;
    @MockBean IOrderService orderService;
    @MockBean IAuthClient authClient;

    @BeforeEach
    void tokens() {
        when(authClient.validateToken("user-token")).thenReturn(ValidateTokenResponseDto.builder()
                .valid(true).userId(7L).email("alice@example.com").roles(List.of("ROLE_USER")).build());
        when(authClient.validateToken("admin-token")).thenReturn(ValidateTokenResponseDto.builder()
                .valid(true).userId(1L).email("admin@example.com").roles(List.of("ROLE_USER", "ROLE_ADMIN")).build());
        when(authClient.validateToken("bad-token")).thenReturn(ValidateTokenResponseDto.builder().valid(false).build());
        when(authClient.validateToken("norole-token")).thenReturn(ValidateTokenResponseDto.builder()
                .valid(true).userId(9L).email("x@example.com").roles(List.of()).build());
    }

    private static final String ADDRESS = """
            {"fullName":"Alice","phoneNumber":"+15551234567","addressLine1":"1 Main St",
             "city":"Pune","state":"MH","postalCode":"411001","country":"IN"}""";

    private static final String ORDER_BODY = """
            {"items":[{"productId":5,"quantity":2}],"deliveryAddress":%s,"paymentMethod":"UPI","expectedTotal":219.90}"""
            .formatted(ADDRESS);

    private static OrderResponseDto orderResponse() {
        return OrderResponseDto.builder()
                .orderNumber(ORDER_NUMBER).status(OrderStatus.PENDING_PAYMENT).paymentMethod(PaymentMethod.UPI)
                .paymentStatus(PaymentStatus.PENDING).currency("INR").totalQuantity(2).totalAmount(new BigDecimal("219.90"))
                .items(List.of(OrderItemResponseDto.builder().productId(5L).title("Backpack")
                        .unitPrice(new BigDecimal("109.95")).quantity(2).lineTotal(new BigDecimal("219.90")).build()))
                .deliveryAddress(DeliveryAddressDto.builder().fullName("Alice").build())
                .createdAt(Instant.parse("2026-10-24T10:00:00Z")).updatedAt(Instant.parse("2026-10-24T10:00:00Z"))
                .build();
    }

    // ---- authentication / authorization

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(orderService);
    }

    @Test
    void invalidToken_is401() throws Exception {
        mockMvc.perform(get("/orders").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenWithoutRoleUser_is403() throws Exception {
        mockMvc.perform(get("/orders").header("Authorization", "Bearer norole-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void authServiceOutage_is503() throws Exception {
        when(authClient.validateToken("down-token")).thenThrow(new DownstreamUnavailableException("user-auth-service"));

        mockMvc.perform(get("/orders").header("Authorization", "Bearer down-token"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void statusUpdate_needsRoleAdmin() throws Exception {
        mockMvc.perform(put("/orders/" + ORDER_NUMBER + "/status").header("Authorization", USER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    // ---- place order

    @Test
    void placeOrder_is201WithTheConfirmationAndUsesTheTokensUser() throws Exception {
        when(orderService.placeOrder(eq(7L), eq("alice@example.com"), any(PlaceOrderRequestDto.class)))
                .thenReturn(orderResponse());

        mockMvc.perform(post("/orders").header("Authorization", USER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.items[0].lineTotal").value(219.90))
                .andExpect(jsonPath("$.paymentLink").doesNotExist());

        ArgumentCaptor<PlaceOrderRequestDto> request = ArgumentCaptor.forClass(PlaceOrderRequestDto.class);
        verify(orderService).placeOrder(eq(7L), eq("alice@example.com"), request.capture());
        assertThat(request.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.UPI);
        assertThat(request.getValue().getExpectedTotal()).isEqualByComparingTo("219.90");
        assertThat(request.getValue().getItems()).hasSize(1);
    }

    @Test
    void placeOrder_validatesTheBody() throws Exception {
        String body = """
                {"items":[{"productId":5,"quantity":0}],"deliveryAddress":{"fullName":""},"paymentMethod":"UPI"}""";

        mockMvc.perform(post("/orders").header("Authorization", USER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").exists())
                .andExpect(jsonPath("$.fieldErrors['deliveryAddress.fullName']").exists())
                .andExpect(jsonPath("$.fieldErrors['deliveryAddress.city']").exists());
        verifyNoInteractions(orderService);
    }

    @Test
    void placeOrder_refusesAnEmptyItemListAndAnUnknownPaymentMethod() throws Exception {
        mockMvc.perform(post("/orders").header("Authorization", USER_TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[],\"deliveryAddress\":" + ADDRESS + ",\"paymentMethod\":\"UPI\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").exists());
        mockMvc.perform(post("/orders").header("Authorization", USER_TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":5,\"quantity\":1}],\"deliveryAddress\":" + ADDRESS + ",\"paymentMethod\":\"BARTER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request"));
    }

    @Test
    void placeOrder_conflictAndOutageAreReported() throws Exception {
        when(orderService.placeOrder(any(), any(), any()))
                .thenThrow(new OrderConflictException("Prices have changed since you reviewed your order, please review it again"))
                .thenThrow(new DownstreamUnavailableException("product-catalog-service"));

        mockMvc.perform(post("/orders").header("Authorization", USER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Prices have changed since you reviewed your order, please review it again"));
        mockMvc.perform(post("/orders").header("Authorization", USER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER_BODY))
                .andExpect(status().isServiceUnavailable());
    }

    // ---- history and lookup

    @Test
    void history_defaultsToTheFirstPageOfTen() throws Exception {
        OrderSummaryResponseDto row = OrderSummaryResponseDto.builder().orderNumber(ORDER_NUMBER)
                .status(OrderStatus.CONFIRMED).paymentStatus(PaymentStatus.PAID).totalQuantity(2)
                .totalAmount(new BigDecimal("219.90")).currency("INR").createdAt(Instant.parse("2026-10-24T10:00:00Z")).build();
        when(orderService.getOrderHistory(7L, 0, 10)).thenReturn(new PageResponseDto<>(List.of(row), 0, 10, 1, 1));

        mockMvc.perform(get("/orders").header("Authorization", USER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void history_passesPageAndSize() throws Exception {
        when(orderService.getOrderHistory(7L, 2, 5)).thenReturn(new PageResponseDto<>(List.of(), 2, 5, 11, 3));

        mockMvc.perform(get("/orders?page=2&size=5").header("Authorization", USER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));
    }

    @Test
    void getOrder_returnsItAndUnknownIs404() throws Exception {
        when(orderService.getOrder(7L, ORDER_NUMBER)).thenReturn(orderResponse());
        when(orderService.getOrder(7L, "ORD-NOPE")).thenThrow(new OrderNotFoundException("ORD-NOPE"));

        mockMvc.perform(get("/orders/" + ORDER_NUMBER).header("Authorization", USER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryAddress.fullName").value("Alice"));
        mockMvc.perform(get("/orders/ORD-NOPE").header("Authorization", USER_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order ORD-NOPE does not exist"));
    }

    @Test
    void tracking_returnsTheTimeline() throws Exception {
        when(orderService.getTracking(7L, ORDER_NUMBER)).thenReturn(OrderTrackingResponseDto.builder()
                .orderNumber(ORDER_NUMBER).status(OrderStatus.SHIPPED)
                .events(List.of(
                        TrackingEventResponseDto.builder().status(OrderStatus.CONFIRMED).timestamp(Instant.parse("2026-10-24T10:00:00Z")).build(),
                        TrackingEventResponseDto.builder().status(OrderStatus.SHIPPED).note("Bluedart 123").timestamp(Instant.parse("2026-10-25T09:00:00Z")).build()))
                .build());

        mockMvc.perform(get("/orders/" + ORDER_NUMBER + "/tracking").header("Authorization", USER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.events[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.events[0].note").doesNotExist())
                .andExpect(jsonPath("$.events[1].note").value("Bluedart 123"));
    }

    // ---- admin status update

    @Test
    void statusUpdate_asAdminReturnsTheTimeline() throws Exception {
        when(orderService.updateStatus(ORDER_NUMBER, OrderStatus.PROCESSING, "Packing"))
                .thenReturn(OrderTrackingResponseDto.builder().orderNumber(ORDER_NUMBER).status(OrderStatus.PROCESSING).events(List.of()).build());

        mockMvc.perform(put("/orders/" + ORDER_NUMBER + "/status").header("Authorization", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PROCESSING\",\"note\":\"Packing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void statusUpdate_conflictAndBadBody() throws Exception {
        when(orderService.updateStatus(ORDER_NUMBER, OrderStatus.DELIVERED, null))
                .thenThrow(new OrderConflictException("Order cannot move from CONFIRMED to DELIVERED"));

        mockMvc.perform(put("/orders/" + ORDER_NUMBER + "/status").header("Authorization", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/orders/" + ORDER_NUMBER + "/status").header("Authorization", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

}
