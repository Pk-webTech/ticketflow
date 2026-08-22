package com.ticketflow.booking.controller;

import com.ticketflow.booking.dto.RevenueSummaryResponse;
import com.ticketflow.booking.service.RevenueService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/bookings/summary")
@PreAuthorize("hasAnyRole('ORGANISER','ADMIN')")
public class RevenueController {

    private final RevenueService revenueService;

    public RevenueController(RevenueService revenueService) {
        this.revenueService = revenueService;
    }

    @GetMapping("/shows/{showId}")
    public RevenueSummaryResponse showSummary(@PathVariable UUID showId) {
        return revenueService.forShow(showId);
    }

    @GetMapping("/events/{eventId}")
    public RevenueSummaryResponse eventSummary(@PathVariable UUID eventId) {
        return revenueService.forEvent(eventId);
    }
}
