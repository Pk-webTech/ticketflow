package com.ticketflow.booking.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BookingSeatResponse(
        UUID seatId,
        String seatLabel,
        String rowLabel,
        Integer seatNumber,
        UUID categoryId,
        String categoryName,
        BigDecimal price
) {
}
