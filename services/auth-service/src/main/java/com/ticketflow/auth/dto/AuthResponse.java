package com.ticketflow.auth.dto;

import com.ticketflow.auth.entity.Role;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresInMs,
        UUID userId,
        String email,
        String fullName,
        Role role
) {
}
