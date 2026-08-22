package com.ticketflow.seathold.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InvalidSeatSelectionException extends ApiException {
    public InvalidSeatSelectionException(UUID seatId) {
        super(HttpStatus.BAD_REQUEST, "Seat '" + seatId + "' does not belong to this show's venue");
    }
}
