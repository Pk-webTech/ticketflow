package com.ticketflow.venueevent.dto;

import java.util.UUID;

public record SeatResponse(
        UUID id,
        UUID venueId,
        UUID categoryId,
        String rowLabel,
        int seatNumber,
        String section
) {
}
