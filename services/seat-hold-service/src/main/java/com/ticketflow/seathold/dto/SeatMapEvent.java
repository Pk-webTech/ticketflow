package com.ticketflow.seathold.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Broadcast whenever seat status changes for a show — held, released
 * (explicit or TTL-expired), or booked. Published to Redis channel
 * {@code seatmap:<showId>} so every service instance (not just the one that
 * handled the originating request) can fan it out over its own WebSocket
 * connections via STOMP.
 */
public record SeatMapEvent(
        UUID showId,
        List<UUID> seatIds,
        SeatStatus status,
        UUID holdId,
        Instant timestamp
) {
    public static SeatMapEvent of(UUID showId, List<UUID> seatIds, SeatStatus status, UUID holdId) {
        return new SeatMapEvent(showId, seatIds, status, holdId, Instant.now());
    }
}
