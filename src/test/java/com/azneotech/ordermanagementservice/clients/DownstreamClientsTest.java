package com.azneotech.ordermanagementservice.clients;

import com.azneotech.ordermanagementservice.dtos.ProductCatalogDto;
import com.azneotech.ordermanagementservice.dtos.ValidateTokenRequestDto;
import com.azneotech.ordermanagementservice.dtos.ValidateTokenResponseDto;
import com.azneotech.ordermanagementservice.exceptions.DownstreamUnavailableException;
import com.azneotech.ordermanagementservice.exceptions.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The clients turn every way a call can fail into a domain exception, so callers answer 503/409 and never 500. */
class DownstreamClientsTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final ProductCatalogClient catalogClient = new ProductCatalogClient(restTemplate);
    private final AuthServiceClient authClient = new AuthServiceClient(restTemplate);

    @Test
    void catalogWithNoRegisteredInstanceIsUnavailable() {
        // What the load balancer throws when Eureka knows no instance of the service.
        when(restTemplate.getForObject(any(String.class), eq(ProductCatalogDto.class), eq(1L)))
                .thenThrow(new IllegalArgumentException("Service Instance cannot be null, serviceId: product-catalog-service"));

        assertThatThrownBy(() -> catalogClient.getProduct(1L)).isInstanceOf(DownstreamUnavailableException.class);
    }

    @Test
    void catalogNotFoundIsProductNotFound() {
        when(restTemplate.getForObject(any(String.class), eq(ProductCatalogDto.class), eq(1L)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThatThrownBy(() -> catalogClient.getProduct(1L)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void authWithNoRegisteredInstanceIsUnavailable() {
        when(restTemplate.postForObject(any(String.class), any(ValidateTokenRequestDto.class), eq(ValidateTokenResponseDto.class)))
                .thenThrow(new IllegalArgumentException("Service Instance cannot be null, serviceId: user-auth-service"));

        assertThatThrownBy(() -> authClient.validateToken("t")).isInstanceOf(DownstreamUnavailableException.class);
    }

    @Test
    void authWithNoRegisteredInstanceViaIllegalStateIsUnavailable() {
        when(restTemplate.postForObject(any(String.class), any(ValidateTokenRequestDto.class), eq(ValidateTokenResponseDto.class)))
                .thenThrow(new IllegalStateException("No instances available"));

        assertThatThrownBy(() -> authClient.validateToken("t")).isInstanceOf(DownstreamUnavailableException.class);
    }

}
