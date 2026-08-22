package com.ticketflow.venueevent.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InvalidSeatCategoryException extends ApiException {
    public InvalidSeatCategoryException(UUID categoryId) {
        super(HttpStatus.BAD_REQUEST, "Seat category '" + categoryId + "' does not exist or does not belong to this venue");
    }
}
