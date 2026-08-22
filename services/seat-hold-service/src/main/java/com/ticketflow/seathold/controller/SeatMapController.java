package com.ticketflow.seathold.controller;

import com.ticketflow.seathold.dto.HoldRequest;
import com.ticketflow.seathold.dto.HoldResponse;
import com.ticketflow.seathold.dto.SeatMapResponse;
import com.ticketflow.seathold.security.AuthenticatedUser;
import com.ticketflow.seathold.service.SeatHoldService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/seats")
public class SeatMapController {

    private final SeatHoldService seatHoldService;

    public SeatMapController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    /**
     * Public — works for anonymous browsers too. When called with a valid
     * JWT, seats held by the caller are flagged HELD_BY_ME instead of
     * HELD_BY_OTHERS so their own UI can show "release" instead of a greyed-out seat.
     */
    @GetMapping("/shows/{showId}/map")
    public ResponseEntity<SeatMapResponse> getSeatMap(
            @PathVariable UUID showId,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        UUID requesterId = principal == null ? null : principal.userId();
        return ResponseEntity.ok(seatHoldService.getSeatMap(showId, requesterId));
    }

    @PostMapping("/shows/{showId}/hold")
    public ResponseEntity<HoldResponse> createHold(
            @PathVariable UUID showId,
            @Valid @RequestBody HoldRequest request,
            @AuthenticationPrincipal AuthenticatedUser customer
    ) {
        HoldResponse response = seatHoldService.createHold(showId, request.seatIds(), customer.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/shows/{showId}/holds/{holdId}")
    public ResponseEntity<Void> releaseHold(
            @PathVariable UUID showId,
            @PathVariable UUID holdId,
            @RequestBody List<UUID> seatIds,
            @AuthenticationPrincipal AuthenticatedUser customer
    ) {
        seatHoldService.releaseHold(showId, holdId, seatIds, customer.userId());
        return ResponseEntity.noContent().build();
    }
}
