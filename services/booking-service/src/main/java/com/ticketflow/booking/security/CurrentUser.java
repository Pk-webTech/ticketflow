package com.ticketflow.booking.security;

import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience accessor for the JWT principal placed by JwtAuthenticationFilter. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new IllegalStateException("No authenticated TicketFlow user on the security context");
    }
}
