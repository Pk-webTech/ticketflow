package com.ticketflow.venueevent.exception;

import org.springframework.http.HttpStatus;

public class NotEventOwnerException extends ApiException {
    public NotEventOwnerException() {
        super(HttpStatus.FORBIDDEN, "You do not have permission to modify this event");
    }
}
