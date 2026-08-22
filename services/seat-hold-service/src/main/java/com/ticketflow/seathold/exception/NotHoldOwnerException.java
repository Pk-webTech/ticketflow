package com.ticketflow.seathold.exception;

import org.springframework.http.HttpStatus;

public class NotHoldOwnerException extends ApiException {
    public NotHoldOwnerException() {
        super(HttpStatus.FORBIDDEN, "You do not own this hold");
    }
}
