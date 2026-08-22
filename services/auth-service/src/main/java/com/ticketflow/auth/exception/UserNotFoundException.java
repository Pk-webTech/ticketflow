package com.ticketflow.auth.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class UserNotFoundException extends ApiException {
    public UserNotFoundException(UUID userId) {
        super(HttpStatus.NOT_FOUND, "No user found with id '" + userId + "'");
    }
}
