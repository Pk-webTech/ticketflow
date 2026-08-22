package com.ticketflow.venueevent.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ShowNotFoundException extends ApiException {
    public ShowNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "No show found with id '" + id + "'");
    }
}
