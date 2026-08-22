package com.ticketflow.waitlist.dto;

import com.ticketflow.waitlist.entity.WaitlistEntry;
import com.ticketflow.waitlist.entity.WaitlistStatus;

import java.time.Instant;
import java.util.UUID;

public record WaitlistEntryResponse(
        UUID id,
        UUID showId,
        UUID categoryId,
        String categoryName,
        UUID customerId,
        int quantity,
        WaitlistStatus status,
        /** 1-based; null unless the entry is ACTIVE. */
        Long position,
        Instant createdAt
) {
    public static WaitlistEntryResponse from(WaitlistEntry e, Long position) {
        return new WaitlistEntryResponse(e.getId(), e.getShowId(), e.getCategoryId(), e.getCategoryName(),
                e.getCustomerId(), e.getQuantity(), e.getStatus(), position, e.getCreatedAt());
    }
}
