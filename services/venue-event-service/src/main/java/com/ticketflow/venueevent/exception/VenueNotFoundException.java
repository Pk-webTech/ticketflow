package com.ticketflow.venueevent.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class VenueNotFoundException extends ApiException {
    public VenueNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "No venue found with id '" + id + "'");
    }
}
