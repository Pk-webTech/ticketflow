package com.ticketflow.waitlist.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email, String role) {
}
