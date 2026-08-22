package com.ticketflow.booking.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Mirrors venue-event-service's {@code EventResponse} — we only need the title. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventDetailsDto(UUID id, String title, String type, String description) {
}
