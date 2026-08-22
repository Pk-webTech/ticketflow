package com.ticketflow.venueevent.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class EventNotFoundException extends ApiException {
    public EventNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "No event found with id '" + id + "'");
    }
}
