package com.ticketflow.booking.dto;

import com.ticketflow.booking.entity.Booking;
import com.ticketflow.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        String bookingReference,
        UUID showId,
        UUID eventId,
        String eventTitle,
        String venueName,
        Instant showStartsAt,
        UUID customerId,
        String customerEmail,
        String customerName,
        BookingStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant cancelledAt,
        List<BookingSeatResponse> seats
) {
    public static BookingResponse from(Booking booking) {
        List<BookingSeatResponse> seats = booking.getSeats().stream()
                .map(s -> new BookingSeatResponse(
                        s.getSeatId(), s.getSeatLabel(), s.getRowLabel(), s.getSeatNumber(),
                        s.getCategoryId(), s.getCategoryName(), s.getPrice()))
                .toList();

        return new BookingResponse(
                booking.getId(),
                booking.getBookingReference(),
                booking.getShowId(),
                booking.getEventId(),
                booking.getEventTitle(),
                booking.getVenueName(),
                booking.getShowStartsAt(),
                booking.getCustomerId(),
                booking.getCustomerEmail(),
                booking.getCustomerName(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getCreatedAt(),
                booking.getCancelledAt(),
                seats
        );
    }
}
