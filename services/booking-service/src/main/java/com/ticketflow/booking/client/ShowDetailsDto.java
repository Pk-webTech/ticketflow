package com.ticketflow.booking.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors venue-event-service's {@code ShowResponse}.
 *
 * <p>Note the field is {@code showDateTime}, not {@code startsAt} — matching
 * the producer exactly matters more than matching our own vocabulary, since a
 * silent null here would put "TBC" on every emailed ticket.
 *
 * <p>{@code ignoreUnknown} so venue-event-service can add fields without
 * breaking deserialisation here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShowDetailsDto(
        UUID id,
        UUID eventId,
        UUID venueId,
        Instant showDateTime,
        String status,
        List<CategoryPriceView> pricing
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryPriceView(UUID categoryId, String categoryName, BigDecimal price) {
    }
}
