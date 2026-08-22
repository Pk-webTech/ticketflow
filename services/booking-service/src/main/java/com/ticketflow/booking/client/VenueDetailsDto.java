package com.ticketflow.booking.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Mirrors venue-event-service's {@code VenueResponse}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VenueDetailsDto(UUID id, String name, String city, String address) {
}
