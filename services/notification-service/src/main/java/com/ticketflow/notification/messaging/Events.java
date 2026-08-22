package com.ticketflow.notification.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consumer-side mirrors of the events booking-service and waitlist-service
 * publish. All lenient: producers may add fields at any time without
 * redeploying this service.
 */
public final class Events {

    private Events() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeatLine(String seatLabel, String categoryName, BigDecimal price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingConfirmed(
            UUID bookingId,
            String bookingReference,
            String customerEmail,
            String customerName,
            String eventTitle,
            String venueName,
            Instant showStartsAt,
            List<SeatLine> seats,
            BigDecimal totalAmount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FreedSeat(UUID seatId, String seatLabel, UUID categoryId, String categoryName, BigDecimal price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingCancelled(
            UUID bookingId,
            String bookingReference,
            String customerEmail,
            String customerName,
            String eventTitle,
            String venueName,
            Instant showStartsAt,
            List<FreedSeat> freedSeats,
            BigDecimal refundAmount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WaitlistOfferCreated(
            UUID offerId,
            String customerEmail,
            String customerName,
            String categoryName,
            String eventTitle,
            String venueName,
            Instant showStartsAt,
            List<String> seatLabels,
            String claimUrl,
            Instant expiresAt,
            long ttlSeconds
    ) {
    }
}
