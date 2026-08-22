package com.ticketflow.waitlist.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mirror of booking-service's published payload (lenient — producer may add fields). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingCancelledEvent(
        UUID bookingId,
        String bookingReference,
        UUID showId,
        UUID eventId,
        String eventTitle,
        String venueName,
        Instant showStartsAt,
        List<FreedSeat> freedSeats,
        Instant occurredAt
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FreedSeat(
            UUID seatId,
            String seatLabel,
            UUID categoryId,
            String categoryName,
            BigDecimal price
    ) {
    }
}
