package com.ticketflow.booking.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published to {@code booking.events} with routing key {@code booking.cancelled}.
 * TWO consumers, which is why it carries both customer-facing and
 * queue-facing data:
 *
 * <ul>
 *   <li>notification-service → cancellation confirmation email</li>
 *   <li>waitlist-service → the freed seats, grouped by category, are what
 *       drives waitlist auto-assignment. {@link FreedSeat#categoryId} is the
 *       key the waitlist queue is partitioned by.</li>
 * </ul>
 */
public record BookingCancelledEvent(
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
        List<FreedSeat> freedSeats,
        BigDecimal refundAmount,
        Instant occurredAt
) {
    public record FreedSeat(
            UUID seatId,
            String seatLabel,
            UUID categoryId,
            String categoryName,
            BigDecimal price
    ) {
    }
}
