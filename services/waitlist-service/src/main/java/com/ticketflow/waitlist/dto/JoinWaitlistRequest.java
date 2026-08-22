package com.ticketflow.waitlist.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record JoinWaitlistRequest(
        @NotNull UUID showId,
        UUID eventId,
        @NotNull UUID categoryId,
        String categoryName,
        @Min(1) int quantity,
        String customerName
) {
}
