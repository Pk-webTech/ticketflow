package com.ticketflow.waitlist.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * → notification-service. Carries everything needed to render the offer email
 * including the fully-formed {@link #claimUrl}, so the notification service
 * never has to know waitlist routing rules.
 */
public record WaitlistOfferCreatedEvent(
        UUID offerId,
        UUID waitlistEntryId,
        UUID showId,
        UUID categoryId,
        String categoryName,
        UUID customerId,
        String customerEmail,
        String customerName,
        String eventTitle,
        String venueName,
        Instant showStartsAt,
        List<String> seatLabels,
        String claimUrl,
        Instant expiresAt,
        long ttlSeconds,
        Instant occurredAt
) {
}
