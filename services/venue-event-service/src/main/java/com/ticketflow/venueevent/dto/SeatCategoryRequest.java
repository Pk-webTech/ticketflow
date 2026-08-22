package com.ticketflow.venueevent.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SeatCategoryRequest(
        @NotBlank String name,
        String displayColor,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal defaultPrice,
        int displayOrder
) {
}
