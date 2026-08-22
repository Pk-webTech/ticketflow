package com.ticketflow.venueevent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShowRequest(
        @NotNull UUID venueId,
        @NotNull @Future Instant showDateTime,
        @NotEmpty @Valid List<CategoryPrice> pricing
) {
    public record CategoryPrice(
            @NotNull UUID categoryId,
            @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal price
    ) {
    }
}
