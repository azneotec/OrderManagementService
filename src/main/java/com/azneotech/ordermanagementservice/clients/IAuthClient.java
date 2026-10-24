package com.azneotech.ordermanagementservice.clients;

import com.azneotech.ordermanagementservice.dtos.ValidateTokenResponseDto;

public interface IAuthClient {

    /** @throws com.azneotech.ordermanagementservice.exceptions.DownstreamUnavailableException if user-auth-service cannot answer */
    ValidateTokenResponseDto validateToken(String token);

}
