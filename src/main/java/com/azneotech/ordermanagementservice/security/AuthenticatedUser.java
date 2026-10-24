package com.azneotech.ordermanagementservice.security;

/** Principal placed in the {@code SecurityContext} by {@link JwtAuthenticationFilter}; orders belong to {@code userId}. */
public record AuthenticatedUser(Long userId, String email) {
}
