package com.ticketflow.venueevent.controller;

import com.ticketflow.venueevent.dto.*;
import com.ticketflow.venueevent.security.AuthenticatedUser;
import com.ticketflow.venueevent.service.VenueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    public ResponseEntity<VenueResponse> createVenue(
            @Valid @RequestBody VenueRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(venueService.createVenue(request, admin.userId()));
    }

    @GetMapping("/{venueId}")
    public ResponseEntity<VenueResponse> getVenue(@PathVariable UUID venueId) {
        return ResponseEntity.ok(venueService.getVenue(venueId));
    }

    @GetMapping
    public ResponseEntity<List<VenueResponse>> listVenues() {
        return ResponseEntity.ok(venueService.listVenues());
    }

    @PutMapping("/{venueId}")
    public ResponseEntity<VenueResponse> updateVenue(
            @PathVariable UUID venueId,
            @Valid @RequestBody VenueRequest request
    ) {
        return ResponseEntity.ok(venueService.updateVenue(venueId, request));
    }

    @DeleteMapping("/{venueId}")
    public ResponseEntity<Void> deleteVenue(@PathVariable UUID venueId) {
        venueService.deleteVenue(venueId);
        return ResponseEntity.noContent().build();
    }

    // ---- Seat categories ------------------------------------------------

    @PostMapping("/{venueId}/categories")
    public ResponseEntity<SeatCategoryResponse> addCategory(
            @PathVariable UUID venueId,
            @Valid @RequestBody SeatCategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(venueService.addCategory(venueId, request));
    }

    @GetMapping("/{venueId}/categories")
    public ResponseEntity<List<SeatCategoryResponse>> listCategories(@PathVariable UUID venueId) {
        return ResponseEntity.ok(venueService.listCategories(venueId));
    }

    // ---- Seat layout ------------------------------------------------------

    @PostMapping("/{venueId}/seats")
    public ResponseEntity<List<SeatResponse>> defineSeatLayout(
            @PathVariable UUID venueId,
            @Valid @RequestBody SeatLayoutRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(venueService.defineSeatLayout(venueId, request));
    }

    @GetMapping("/{venueId}/seats")
    public ResponseEntity<List<SeatResponse>> getSeatLayout(@PathVariable UUID venueId) {
        return ResponseEntity.ok(venueService.getSeatLayout(venueId));
    }
}
