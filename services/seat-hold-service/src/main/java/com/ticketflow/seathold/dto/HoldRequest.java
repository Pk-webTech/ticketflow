package com.ticketflow.seathold.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record HoldRequest(
        @NotEmpty @Size(max = 10, message = "Cannot hold more than 10 seats at once")
        List<UUID> seatIds
) {
}
