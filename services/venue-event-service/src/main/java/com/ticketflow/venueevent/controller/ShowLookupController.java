package com.ticketflow.venueevent.controller;

import com.ticketflow.venueevent.dto.ShowResponse;
import com.ticketflow.venueevent.service.ShowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Flat show lookup for internal service-to-service calls (seat-hold-service,
 * booking-service, waitlist-service) that only carry a showId, not the
 * parent eventId required by the nested {@code /api/events/{eventId}/shows/{showId}} route.
 */
@RestController
@RequestMapping("/api/shows")
public class ShowLookupController {

    private final ShowService showService;

    public ShowLookupController(ShowService showService) {
        this.showService = showService;
    }

    @GetMapping("/{showId}")
    public ResponseEntity<ShowResponse> getShow(@PathVariable UUID showId) {
        return ResponseEntity.ok(showService.getShow(showId));
    }
}
