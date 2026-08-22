package com.ticketflow.venueevent.dto;

import com.ticketflow.venueevent.entity.EventType;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        UUID organiserId,
        String title,
        EventType type,
        String description,
        String language,
        Integer durationMinutes,
        String posterUrl,
        String status,
        Instant createdAt
) {
}
