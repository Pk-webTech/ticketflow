package com.ticketflow.booking.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published to {@code booking.events} with routing key {@code booking.confirmed}.
 * Consumed by notification-service, which generates a QR code encoding
 * {@link #bookingReference} and emails the ticket.
 *
 * <p>The payload is deliberately SELF-CONTAINED: notification-service should
 * never have to call back into booking-service or venue-event-service to
 * render an email. That keeps the consumer stateless and the email pipeline
 * resilient to booking-service downtime.
 */
public record BookingConfirmedEvent(
        UUID bookingId,
        String bookingReference,
        UUID customerId,
        String customerEmail,
        String customerName,
        UUID showId,
        UUID eventId,
        String eventTitle,
        String venueName,
        Instant showStartsAt,
        List<SeatLine> seats,
        BigDecimal totalAmount,
        Instant occurredAt
) {
    public record SeatLine(String seatLabel, String categoryName, BigDecimal price) {
    }
}
