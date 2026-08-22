package com.ticketflow.auth.dto;

import com.ticketflow.auth.entity.Role;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        Role role,
        boolean enabled,
        Instant createdAt
) {
}
