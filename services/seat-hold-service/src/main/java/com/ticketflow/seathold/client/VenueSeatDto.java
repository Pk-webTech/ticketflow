package com.ticketflow.seathold.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record VenueSeatDto(
        UUID id,
        UUID venueId,
        UUID categoryId,
        String rowLabel,
        int seatNumber,
        String section
) {
    /** Wraps venue-event-service's ShowResponse just enough to read venueId + pricing. */
    public record ShowInfo(
            UUID id,
            UUID eventId,
            UUID venueId,
            String showDateTime,
            String status,
            List<CategoryPriceView> pricing
    ) {
        public record CategoryPriceView(UUID categoryId, String categoryName, BigDecimal price) {
        }
    }

    public record SeatCategoryDto(UUID id, UUID venueId, String name, String displayColor, BigDecimal defaultPrice, int displayOrder) {
    }
}
