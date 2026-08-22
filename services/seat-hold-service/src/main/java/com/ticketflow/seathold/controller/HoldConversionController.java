package com.ticketflow.seathold.controller;

import com.ticketflow.seathold.service.SeatHoldService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Internal, service-to-service endpoint used by booking-service once a
 * booking is confirmed for a hold — releases the Redis TTL entry (no longer
 * needed since the seat is now permanently booked) and marks the audit trail
 * CONVERTED. Not exposed to end users; called with a valid CUSTOMER-role
 * token obtained on the customer's behalf (booking-service forwards the
 * original request's bearer token).
 */
@RestController
@RequestMapping("/api/seats/internal")
public class HoldConversionController {

    private final SeatHoldService seatHoldService;

    public HoldConversionController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    public record ConvertRequest(UUID showId, UUID holdId, List<UUID> seatIds, UUID customerId) {
    }

    @PostMapping("/convert")
    public ResponseEntity<Void> markConverted(@RequestBody ConvertRequest request) {
        seatHoldService.markConverted(request.showId(), request.holdId(), request.seatIds(), request.customerId());
        return ResponseEntity.noContent().build();
    }
}
