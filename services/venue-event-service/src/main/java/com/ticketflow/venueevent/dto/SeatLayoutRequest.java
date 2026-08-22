package com.ticketflow.venueevent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.util.UUID;

/**
 * Admin submits one block per category — e.g. rows A–E, 20 seats each, mapped
 * to the "Premium" category — and the service expands it into individual
 * {@code Seat} rows. Keeps venue seat-layout setup to a handful of API calls
 * instead of one per seat.
 */
public record SeatLayoutRequest(
        @NotEmpty @Valid List<RowBlock> blocks
) {
    public record RowBlock(
            @NotNull UUID categoryId,
            @NotBlank String rowLabel,
            @Min(1) int seatCount,
            String section
    ) {
    }
}
