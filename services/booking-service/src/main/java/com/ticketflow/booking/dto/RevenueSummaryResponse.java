package com.ticketflow.booking.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Organiser-facing booking + revenue summary. {@code perCategory} lets an
 * organiser see which price tiers are actually selling.
 */
public record RevenueSummaryResponse(
        UUID scopeId,
        String scope,               // "SHOW" or "EVENT"
        long confirmedBookings,
        long cancelledBookings,
        long seatsSold,
        BigDecimal grossRevenue,
        BigDecimal refundedAmount,
        BigDecimal netRevenue,
        List<CategoryBreakdown> perCategory
) {
    public record CategoryBreakdown(
            UUID categoryId,
            String categoryName,
            long seatsSold,
            BigDecimal revenue
    ) {
    }
}
