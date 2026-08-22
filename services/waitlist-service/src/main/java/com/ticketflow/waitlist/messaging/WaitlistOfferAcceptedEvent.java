package com.ticketflow.waitlist.messaging;

import java.util.List;
import java.util.UUID;

/**
 * → seat-hold-service, which converts the accepted offer into a normal
 * TTL-backed Redis hold. Deliberately identical in shape to the record
 * seat-hold-service already consumes, so the customer then walks the exact
 * same checkout path as any other buyer — no parallel booking flow to
 * maintain or secure.
 */
public record WaitlistOfferAcceptedEvent(
        UUID offerId,
        UUID showId,
        UUID customerId,
        List<UUID> seatIds
) {
}
