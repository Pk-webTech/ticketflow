package com.ticketflow.venueevent.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SeatCategoryResponse(
        UUID id,
        UUID venueId,
        String name,
        String displayColor,
        BigDecimal defaultPrice,
        int displayOrder
) {
}
