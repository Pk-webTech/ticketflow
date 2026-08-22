package com.ticketflow.seathold.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class HoldNotFoundException extends ApiException {
    public HoldNotFoundException(UUID holdId) {
        super(HttpStatus.NOT_FOUND, "No active hold found with id '" + holdId + "' (it may have already expired or been released)");
    }
}
