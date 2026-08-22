package com.ticketflow.venueevent.dto;

import com.ticketflow.venueevent.entity.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EventRequest(
        @NotBlank String title,
        @NotNull EventType type,
        String description,
        String language,
        @Positive Integer durationMinutes,
        String posterUrl
) {
}
