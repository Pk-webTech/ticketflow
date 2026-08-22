package com.ticketflow.auth.exception;

import org.springframework.http.HttpStatus;

public class SelfRegistrationNotAllowedException extends ApiException {
    public SelfRegistrationNotAllowedException() {
        super(HttpStatus.FORBIDDEN, "ADMIN accounts cannot be self-registered; ask an existing admin to create one");
    }
}
