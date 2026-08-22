package com.ticketflow.seathold.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class SeatUnavailableException extends ApiException {
    public SeatUnavailableException(UUID seatId) {
        super(HttpStatus.CONFLICT, "Seat '" + seatId + "' is no longer available — it was just held or booked by someone else");
    }
}
