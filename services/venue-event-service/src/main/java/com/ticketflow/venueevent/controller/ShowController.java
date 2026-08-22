package com.ticketflow.venueevent.controller;

import com.ticketflow.venueevent.dto.ShowRequest;
import com.ticketflow.venueevent.dto.ShowResponse;
import com.ticketflow.venueevent.security.AuthenticatedUser;
import com.ticketflow.venueevent.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping
    public ResponseEntity<ShowResponse> createShow(
            @PathVariable UUID eventId,
            @Valid @RequestBody ShowRequest request,
            @AuthenticationPrincipal AuthenticatedUser organiser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(showService.createShow(eventId, request, organiser.userId()));
    }

    @GetMapping
    public ResponseEntity<List<ShowResponse>> listShows(@PathVariable UUID eventId) {
        return ResponseEntity.ok(showService.listShowsForEvent(eventId));
    }

    @GetMapping("/{showId}")
    public ResponseEntity<ShowResponse> getShow(@PathVariable UUID eventId, @PathVariable UUID showId) {
        return ResponseEntity.ok(showService.getShow(showId));
    }

    @DeleteMapping("/{showId}")
    public ResponseEntity<Void> cancelShow(
            @PathVariable UUID eventId,
            @PathVariable UUID showId,
            @AuthenticationPrincipal AuthenticatedUser organiser
    ) {
        showService.cancelShow(showId, organiser.userId());
        return ResponseEntity.noContent().build();
    }
}
