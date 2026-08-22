package com.ticketflow.waitlist.exception;

public final class WaitlistExceptions {

    private WaitlistExceptions() {
    }

    public static class AlreadyOnWaitlistException extends RuntimeException {
        public AlreadyOnWaitlistException() {
            super("You are already on the waitlist for this seat category.");
        }
    }

    public static class EntryNotFoundException extends RuntimeException {
        public EntryNotFoundException(Object id) {
            super("Waitlist entry not found: " + id);
        }
    }

    public static class OfferNotFoundException extends RuntimeException {
        public OfferNotFoundException() {
            super("This seat offer link is not valid.");
        }
    }

    /** The single most user-visible failure: they clicked the link too late. */
    public static class OfferExpiredException extends RuntimeException {
        public OfferExpiredException() {
            super("This offer has expired and the seats have been passed to the next customer in the queue.");
        }
    }

    public static class OfferAlreadyResolvedException extends RuntimeException {
        public OfferAlreadyResolvedException() {
            super("This offer has already been used.");
        }
    }

    /** Method-reference-friendly factory for orElseThrow. */
    public static OfferNotFoundException new_offerNotFound() {
        return new OfferNotFoundException();
    }

    public static class NotEntryOwnerException extends RuntimeException {
        public NotEntryOwnerException() {
            super("You can only manage your own waitlist entries.");
        }
    }
}
