package com.azneotech.ordermanagementservice.clients;

import com.azneotech.ordermanagementservice.dtos.ValidateTokenRequestDto;
import com.azneotech.ordermanagementservice.dtos.ValidateTokenResponseDto;
import com.azneotech.ordermanagementservice.exceptions.DownstreamUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Calls UserAuthService by its Eureka name through the {@code @LoadBalanced} RestTemplate. */
@Service
public class AuthServiceClient implements IAuthClient {

    private static final String SERVICE = "user-auth-service";
    private static final String VALIDATE_TOKEN_URL = "http://" + SERVICE + "/auth/validateToken";

    private final RestTemplate restTemplate;

    public AuthServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public ValidateTokenResponseDto validateToken(String token) {
        try {
            ValidateTokenResponseDto response = restTemplate.postForObject(
                    VALIDATE_TOKEN_URL, new ValidateTokenRequestDto(token), ValidateTokenResponseDto.class);
            if (response == null) {
                throw new DownstreamUnavailableException(SERVICE);
            }
            return response;
        } catch (RestClientException | IllegalStateException | IllegalArgumentException e) {
            // The load balancer throws IllegalStateException or IllegalArgumentException when no instance is registered.
            throw new DownstreamUnavailableException(SERVICE, e);
        }
    }

}
