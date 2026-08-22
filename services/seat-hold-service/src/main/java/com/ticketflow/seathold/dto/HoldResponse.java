package com.ticketflow.seathold.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HoldResponse(
        UUID holdId,
        UUID showId,
        UUID customerId,
        List<UUID> seatIds,
        Instant expiresAt,
        long ttlSeconds
) {
}
