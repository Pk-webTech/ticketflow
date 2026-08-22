package com.ticketflow.booking.exception;

import java.util.UUID;

/** All booking-service domain failures, each mapped to an HTTP status in GlobalExceptionHandler. */
public final class BookingExceptions {

    private BookingExceptions() {
    }

    /** 409 — the seat was booked by someone else between hold check and insert. */
    public static class SeatAlreadyBookedException extends RuntimeException {
        public SeatAlreadyBookedException() {
            super("One or more of these seats has just been booked by another customer. Please pick different seats.");
        }
    }

    /** 409 — the Redis hold lapsed (TTL expired) before checkout completed. */
    public static class HoldExpiredException extends RuntimeException {
        public HoldExpiredException(UUID seatId) {
            super("Your seat hold has expired (seat " + seatId + " was auto-released). Please select seats again.");
        }
    }

    /** 403 — the hold exists but belongs to a different customer. */
    public static class NotHoldOwnerException extends RuntimeException {
        public NotHoldOwnerException() {
            super("This seat hold belongs to a different customer.");
        }
    }

    /** 404 */
    public static class BookingNotFoundException extends RuntimeException {
        public BookingNotFoundException(Object ref) {
            super("Booking not found: " + ref);
        }
    }

    /** 403 */
    public static class NotBookingOwnerException extends RuntimeException {
        public NotBookingOwnerException() {
            super("You can only act on your own bookings.");
        }
    }

    /** 409 */
    public static class BookingAlreadyCancelledException extends RuntimeException {
        public BookingAlreadyCancelledException() {
            super("This booking has already been cancelled.");
        }
    }

    /** 409 — cancellation window closed (show already started). */
    public static class CancellationWindowClosedException extends RuntimeException {
        public CancellationWindowClosedException() {
            super("Bookings cannot be cancelled after the show has started.");
        }
    }

    /** 502 — an upstream service (venue-event / seat-hold) is unreachable or errored. */
    public static class UpstreamServiceException extends RuntimeException {
        public UpstreamServiceException(String service, String detail) {
            super("Upstream service '" + service + "' failed: " + detail);
        }
    }
}
