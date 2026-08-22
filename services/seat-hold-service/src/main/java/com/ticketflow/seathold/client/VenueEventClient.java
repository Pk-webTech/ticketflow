package com.ticketflow.seathold.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class VenueEventClient {

    private final RestClient restClient;

    public VenueEventClient(@Value("${services.venue-event.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Fetches the show (venueId + per-category pricing) — public GET, no auth needed. */
    public VenueSeatDto.ShowInfo getShowInfo(UUID showId) {
        return restClient.get()
                .uri("/api/shows/{showId}", showId)
                .retrieve()
                .body(VenueSeatDto.ShowInfo.class);
    }

    public List<VenueSeatDto> getVenueSeats(UUID venueId) {
        return restClient.get()
                .uri("/api/venues/{venueId}/seats", venueId)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<VenueSeatDto>>() {});
    }

    public List<VenueSeatDto.SeatCategoryDto> getVenueCategories(UUID venueId) {
        return restClient.get()
                .uri("/api/venues/{venueId}/categories", venueId)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<VenueSeatDto.SeatCategoryDto>>() {});
    }
}
