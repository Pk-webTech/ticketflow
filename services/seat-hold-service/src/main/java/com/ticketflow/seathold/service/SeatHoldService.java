package com.ticketflow.seathold.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.seathold.client.VenueEventClient;
import com.ticketflow.seathold.client.VenueSeatDto;
import com.ticketflow.seathold.dto.*;
import com.ticketflow.seathold.entity.HoldAudit;
import com.ticketflow.seathold.entity.HoldStatus;
import com.ticketflow.seathold.exception.HoldNotFoundException;
import com.ticketflow.seathold.exception.InvalidSeatSelectionException;
import com.ticketflow.seathold.exception.NotHoldOwnerException;
import com.ticketflow.seathold.exception.SeatUnavailableException;
import com.ticketflow.seathold.repository.HoldAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SeatHoldService {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldService.class);

    private final SeatHoldRedisService redisService;
    private final VenueEventClient venueEventClient;
    private final HoldAuditRepository holdAuditRepository;
    private final ObjectMapper objectMapper;

    public SeatHoldService(
            SeatHoldRedisService redisService,
            VenueEventClient venueEventClient,
            HoldAuditRepository holdAuditRepository,
            ObjectMapper objectMapper
    ) {
        this.redisService = redisService;
        this.venueEventClient = venueEventClient;
        this.holdAuditRepository = holdAuditRepository;
        this.objectMapper = objectMapper;
    }

    // ---- Seat map --------------------------------------------------------

    public SeatMapResponse getSeatMap(UUID showId, UUID requestingCustomerId) {
        VenueSeatDto.ShowInfo showInfo = venueEventClient.getShowInfo(showId);
        List<VenueSeatDto> seats = venueEventClient.getVenueSeats(showInfo.venueId());
        List<VenueSeatDto.SeatCategoryDto> categories = venueEventClient.getVenueCategories(showInfo.venueId());

        Map<UUID, String> categoryNames = categories.stream()
                .collect(Collectors.toMap(VenueSeatDto.SeatCategoryDto::id, VenueSeatDto.SeatCategoryDto::name));
        Map<UUID, String> categoryColors = categories.stream()
                .collect(Collectors.toMap(VenueSeatDto.SeatCategoryDto::id, c -> c.displayColor() == null ? "#CCCCCC" : c.displayColor()));
        Map<UUID, java.math.BigDecimal> categoryPrices = showInfo.pricing().stream()
                .collect(Collectors.toMap(VenueSeatDto.ShowInfo.CategoryPriceView::categoryId, VenueSeatDto.ShowInfo.CategoryPriceView::price));

        List<SeatMapCell> cells = seats.stream()
                .map(seat -> {
                    SeatStatus status = resolveStatus(showId, seat.id(), requestingCustomerId);
                    return new SeatMapCell(
                            seat.id(), seat.rowLabel(), seat.seatNumber(), seat.section(),
                            seat.categoryId(), categoryNames.getOrDefault(seat.categoryId(), "Unknown"),
                            categoryColors.getOrDefault(seat.categoryId(), "#CCCCCC"),
                            categoryPrices.get(seat.categoryId()),
                            status
                    );
                })
                .toList();

        return new SeatMapResponse(showId, cells);
    }

    private SeatStatus resolveStatus(UUID showId, UUID seatId, UUID requestingCustomerId) {
        UUID holder = redisService.getHoldingCustomer(showId, seatId);
        if (holder == null) {
            return SeatStatus.AVAILABLE; // BOOKED seats are excluded once booking-service integration lands (Phase 5)
        }
        return holder.equals(requestingCustomerId) ? SeatStatus.HELD_BY_ME : SeatStatus.HELD_BY_OTHERS;
    }

    // ---- Hold lifecycle ------------------------------------------------

    @Transactional
    public HoldResponse createHold(UUID showId, List<UUID> seatIds, UUID customerId) {
        validateSeatsBelongToShow(showId, seatIds);

        UUID holdId = UUID.randomUUID();
        long ttl = redisService.getHoldTtlSeconds();

        UUID blockingSeat = redisService.tryAcquireHold(showId, seatIds, holdId, customerId, ttl);
        if (blockingSeat != null) {
            throw new SeatUnavailableException(blockingSeat);
        }

        persistAudit(holdId, showId, customerId, seatIds, HoldStatus.CREATED, ttl);
        broadcast(showId, seatIds, SeatStatus.HELD_BY_OTHERS, holdId); // recipients resolve HELD_BY_ME client-side by comparing customerId

        Instant expiresAt = Instant.now().plusSeconds(ttl);
        return new HoldResponse(holdId, showId, customerId, seatIds, expiresAt, ttl);
    }

    @Transactional
    public void releaseHold(UUID showId, UUID holdId, List<UUID> seatIds, UUID customerId) {
        long released = redisService.release(showId, seatIds, holdId);
        if (released == 0) {
            throw new HoldNotFoundException(holdId);
        }

        updateAuditStatus(holdId, HoldStatus.RELEASED);
        broadcast(showId, seatIds, SeatStatus.AVAILABLE, null);
    }

    /**
     * Used by booking-service (via internal call) when a hold successfully
     * converts into a confirmed booking — releases the TTL entry (it's no
     * longer needed; the seat is now permanently booked) without broadcasting
     * AVAILABLE, since booking-service will broadcast BOOKED itself.
     */
    @Transactional
    public void markConverted(UUID showId, UUID holdId, List<UUID> seatIds, UUID customerId) {
        for (UUID seatId : seatIds) {
            UUID holder = redisService.getHoldingCustomer(showId, seatId);
            if (holder != null && !holder.equals(customerId)) {
                throw new NotHoldOwnerException();
            }
        }
        redisService.release(showId, seatIds, holdId);
        updateAuditStatus(holdId, HoldStatus.CONVERTED);
    }

    // ---- helpers ------------------------------------------------------

    private void validateSeatsBelongToShow(UUID showId, List<UUID> seatIds) {
        VenueSeatDto.ShowInfo showInfo = venueEventClient.getShowInfo(showId);
        Set<UUID> validSeatIds = venueEventClient.getVenueSeats(showInfo.venueId()).stream()
                .map(VenueSeatDto::id)
                .collect(Collectors.toSet());

        for (UUID seatId : seatIds) {
            if (!validSeatIds.contains(seatId)) {
                throw new InvalidSeatSelectionException(seatId);
            }
        }
    }

    private void persistAudit(UUID holdId, UUID showId, UUID customerId, List<UUID> seatIds, HoldStatus status, long ttl) {
        HoldAudit audit = HoldAudit.builder()
                .holdId(holdId)
                .showId(showId)
                .customerId(customerId)
                .seatIds(seatIds.stream().map(UUID::toString).collect(Collectors.joining(",")))
                .status(status)
                .ttlSeconds(ttl)
                .build();
        holdAuditRepository.save(audit);
    }

    private void updateAuditStatus(UUID holdId, HoldStatus status) {
        holdAuditRepository.findById(holdId).ifPresent(audit -> {
            audit.setStatus(status);
            audit.setResolvedAt(Instant.now());
            holdAuditRepository.save(audit);
        });
    }

    private void broadcast(UUID showId, List<UUID> seatIds, SeatStatus status, UUID holdId) {
        try {
            SeatMapEvent event = SeatMapEvent.of(showId, seatIds, status, holdId);
            redisService.publishSeatMapEvent(showId, objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            log.warn("Failed to broadcast seat-map event for show {}: {}", showId, ex.getMessage());
        }
    }
}
