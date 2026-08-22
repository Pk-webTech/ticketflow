package com.ticketflow.seathold.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SeatMapCell(
        UUID seatId,
        String rowLabel,
        int seatNumber,
        String section,
        UUID categoryId,
        String categoryName,
        String displayColor,
        BigDecimal price,
        SeatStatus status
) {
}
