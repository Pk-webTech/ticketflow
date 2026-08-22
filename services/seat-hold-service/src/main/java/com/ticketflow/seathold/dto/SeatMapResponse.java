package com.ticketflow.seathold.dto;

import java.util.List;
import java.util.UUID;

public record SeatMapResponse(
        UUID showId,
        List<SeatMapCell> seats
) {
}
