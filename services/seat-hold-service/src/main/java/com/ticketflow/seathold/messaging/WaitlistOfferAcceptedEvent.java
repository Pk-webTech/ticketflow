package com.ticketflow.seathold.messaging;

import java.util.List;
import java.util.UUID;

/**
 * Published by waitlist-service to {@code waitlist.events} exchange (routing
 * key {@code waitlist.offer.accepted}) when a waitlisted customer accepts
 * their time-limited seat offer. seat-hold-service consumes it and converts
 * the offer into a real, TTL-backed hold so the customer can complete
 * checkout through the normal booking flow.
 */
public record WaitlistOfferAcceptedEvent(
        UUID offerId,
        UUID showId,
        UUID customerId,
        List<UUID> seatIds
) {
}
