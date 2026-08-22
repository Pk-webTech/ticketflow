package com.ticketflow.seathold.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.seathold.client.VenueEventClient;
import com.ticketflow.seathold.client.VenueSeatDto;
import com.ticketflow.seathold.dto.HoldResponse;
import com.ticketflow.seathold.exception.HoldNotFoundException;
import com.ticketflow.seathold.exception.InvalidSeatSelectionException;
import com.ticketflow.seathold.exception.SeatUnavailableException;
import com.ticketflow.seathold.repository.HoldAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SeatHoldServiceTest {

    @Mock
    private SeatHoldRedisService redisService;
    @Mock
    private VenueEventClient venueEventClient;
    @Mock
    private HoldAuditRepository holdAuditRepository;

    private SeatHoldService seatHoldService;

    private final UUID showId = UUID.randomUUID();
    private final UUID venueId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID seatId1 = UUID.randomUUID();
    private final UUID seatId2 = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        seatHoldService = new SeatHoldService(redisService, venueEventClient, holdAuditRepository, new ObjectMapper());

        VenueSeatDto.ShowInfo showInfo = new VenueSeatDto.ShowInfo(
                showId, UUID.randomUUID(), venueId, "2026-09-01T18:00:00Z", "SCHEDULED", List.of());
        when(venueEventClient.getShowInfo(showId)).thenReturn(showInfo);

        List<VenueSeatDto> seats = List.of(
                new VenueSeatDto(seatId1, venueId, categoryId, "A", 1, null),
                new VenueSeatDto(seatId2, venueId, categoryId, "A", 2, null)
        );
        when(venueEventClient.getVenueSeats(venueId)).thenReturn(seats);
    }

    @Test
    void createHoldSucceedsWhenAllSeatsFree() {
        when(redisService.getHoldTtlSeconds()).thenReturn(600L);
        when(redisService.tryAcquireHold(eq(showId), anyList(), any(), eq(customerId), eq(600L)))
                .thenReturn(null); // null = success, nothing blocking

        HoldResponse response = seatHoldService.createHold(showId, List.of(seatId1, seatId2), customerId);

        assertThat(response.showId()).isEqualTo(showId);
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.seatIds()).containsExactly(seatId1, seatId2);
        assertThat(response.ttlSeconds()).isEqualTo(600L);
        verify(holdAuditRepository).save(any());
    }

    @Test
    void createHoldFailsWhenASeatIsAlreadyTaken() {
        when(redisService.getHoldTtlSeconds()).thenReturn(600L);
        when(redisService.tryAcquireHold(eq(showId), anyList(), any(), eq(customerId), eq(600L)))
                .thenReturn(seatId2); // seatId2 is blocking

        assertThatThrownBy(() -> seatHoldService.createHold(showId, List.of(seatId1, seatId2), customerId))
                .isInstanceOf(SeatUnavailableException.class)
                .hasMessageContaining(seatId2.toString());

        // Nothing should be persisted to the audit trail on failure.
        verify(holdAuditRepository, never()).save(any());
    }

    @Test
    void createHoldRejectsSeatNotBelongingToVenue() {
        UUID rogueSeatId = UUID.randomUUID(); // not in the venue's seat list

        assertThatThrownBy(() -> seatHoldService.createHold(showId, List.of(rogueSeatId), customerId))
                .isInstanceOf(InvalidSeatSelectionException.class);

        verifyNoInteractions(holdAuditRepository);
    }

    @Test
    void releaseHoldThrowsWhenNothingWasReleased() {
        UUID holdId = UUID.randomUUID();
        when(redisService.release(eq(showId), anyList(), eq(holdId))).thenReturn(0L);

        assertThatThrownBy(() -> seatHoldService.releaseHold(showId, holdId, List.of(seatId1), customerId))
                .isInstanceOf(HoldNotFoundException.class);
    }

    @Test
    void releaseHoldSucceedsAndUpdatesAudit() {
        UUID holdId = UUID.randomUUID();
        when(redisService.release(eq(showId), anyList(), eq(holdId))).thenReturn(1L);

        seatHoldService.releaseHold(showId, holdId, List.of(seatId1), customerId);

        verify(redisService).publishSeatMapEvent(eq(showId), any());
    }
}
