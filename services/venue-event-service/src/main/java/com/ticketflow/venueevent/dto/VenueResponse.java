package com.ticketflow.venueevent.dto;

import java.time.Instant;
import java.util.UUID;

public record VenueResponse(
        UUID id,
        String name,
        String address,
        String city,
        String state,
        String postalCode,
        int totalCapacity,
        UUID createdBy,
        Instant createdAt
) {
}
