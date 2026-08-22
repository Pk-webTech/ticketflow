package com.ticketflow.venueevent.controller;

import com.ticketflow.venueevent.dto.EventRequest;
import com.ticketflow.venueevent.dto.EventResponse;
import com.ticketflow.venueevent.security.AuthenticatedUser;
import com.ticketflow.venueevent.service.EventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody EventRequest request,
            @AuthenticationPrincipal AuthenticatedUser organiser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(request, organiser.userId()));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable UUID eventId) {
        return ResponseEntity.ok(eventService.getEvent(eventId));
    }

    @GetMapping
    public ResponseEntity<Page<EventResponse>> listEvents(Pageable pageable) {
        return ResponseEntity.ok(eventService.listEvents(pageable));
    }

    @GetMapping("/mine")
    public ResponseEntity<Page<EventResponse>> listMyEvents(
            @AuthenticationPrincipal AuthenticatedUser organiser,
            Pageable pageable
    ) {
        return ResponseEntity.ok(eventService.listMyEvents(organiser.userId(), pageable));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventRequest request,
            @AuthenticationPrincipal AuthenticatedUser organiser
    ) {
        return ResponseEntity.ok(eventService.updateEvent(eventId, request, organiser.userId()));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> delistEvent(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUser organiser
    ) {
        eventService.delistEvent(eventId, organiser.userId());
        return ResponseEntity.noContent().build();
    }
}
