package com.ticketflow.venueevent.dto;

import com.ticketflow.venueevent.entity.ShowStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShowResponse(
        UUID id,
        UUID eventId,
        UUID venueId,
        Instant showDateTime,
        ShowStatus status,
        List<CategoryPriceView> pricing
) {
    public record CategoryPriceView(UUID categoryId, String categoryName, BigDecimal price) {
    }
}
