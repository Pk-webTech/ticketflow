package com.ticketflow.booking.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Mirrors venue-event-service's {@code SeatResponse}.
 *
 * <p>That payload has no human label — it carries {@code rowLabel} and
 * {@code seatNumber} separately — so we compose "A12" here. This is the string
 * printed on the ticket and shown in booking history, so it's built once,
 * centrally, rather than in each caller.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VenueSeatDto(
        UUID id,
        UUID venueId,
        UUID categoryId,
        String rowLabel,
        Integer seatNumber,
        String section
) {
    public String displayLabel() {
        return (rowLabel == null ? "" : rowLabel) + (seatNumber == null ? "" : seatNumber);
    }
}
