package com.ticketflow.venueevent.dto;

public record ShowSearchFilters(
        String query,
        String city,
        String eventType,
        String fromDate,   // ISO-8601 instant, optional
        String toDate      // ISO-8601 instant, optional
) {
}
