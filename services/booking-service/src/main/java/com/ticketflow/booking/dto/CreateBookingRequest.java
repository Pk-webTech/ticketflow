package com.ticketflow.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID showId,
        @NotNull UUID holdId,
        @NotEmpty List<UUID> seatIds,
        String customerName
) {
}
