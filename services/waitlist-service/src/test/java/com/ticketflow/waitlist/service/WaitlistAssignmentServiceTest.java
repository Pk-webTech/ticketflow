package com.ticketflow.waitlist.service;

import com.ticketflow.waitlist.entity.*;
import com.ticketflow.waitlist.messaging.BookingCancelledEvent;
import com.ticketflow.waitlist.messaging.WaitlistOfferCreatedEvent;
import com.ticketflow.waitlist.repository.SeatOfferRepository;
import com.ticketflow.waitlist.repository.WaitlistEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WaitlistAssignmentServiceTest {

    @Mock WaitlistEntryRepository entryRepository;
    @Mock SeatOfferRepository offerRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    WaitlistAssignmentService service;

    final UUID showId = UUID.randomUUID();
    final UUID premium = UUID.randomUUID();
    final UUID standard = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WaitlistAssignmentService(
                entryRepository, offerRepository, eventPublisher, 900, "http://localhost");
        when(offerRepository.save(any(SeatOffer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(entryRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private WaitlistEntry waiter(UUID categoryId, int quantity, Instant joinedAt) {
        return WaitlistEntry.builder()
                .id(UUID.randomUUID()).showId(showId).categoryId(categoryId)
                .customerId(UUID.randomUUID()).customerEmail("w@example.com")
                .quantity(quantity).status(WaitlistStatus.ACTIVE).createdAt(joinedAt)
                .build();
    }

    private BookingCancelledEvent cancellation(BookingCancelledEvent.FreedSeat... seats) {
        return new BookingCancelledEvent(UUID.randomUUID(), "TF-X", showId, UUID.randomUUID(),
                "Interstellar", "PVR Grand", Instant.now().plusSeconds(86400), List.of(seats), Instant.now());
    }

    private BookingCancelledEvent.FreedSeat seat(UUID categoryId, String label) {
        return new BookingCancelledEvent.FreedSeat(
                UUID.randomUUID(), label, categoryId, "Cat", new BigDecimal("100"));
    }

    @Test
    void offersFreedSeatToHeadOfTheMatchingCategoryQueue() {
        WaitlistEntry head = waiter(premium, 1, Instant.now().minusSeconds(600));
        when(entryRepository.lockNextInQueue(eq(showId), eq(premium), any(Pageable.class)))
                .thenReturn(List.of(head), List.of());

        service.assignFreedSeats(cancellation(seat(premium, "A1")));

        ArgumentCaptor<SeatOffer> captor = ArgumentCaptor.forClass(SeatOffer.class);
        verify(offerRepository).save(captor.capture());
        SeatOffer offer = captor.getValue();

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(offer.getCategoryId()).isEqualTo(premium);
        assertThat(offer.getExpiresAt()).isAfter(Instant.now());
        assertThat(offer.getToken()).hasSizeGreaterThan(20);   // unguessable

        // the waiter leaves the queue while their offer is live
        assertThat(head.getStatus()).isEqualTo(WaitlistStatus.OFFERED);
        verify(eventPublisher).publishEvent(any(WaitlistOfferCreatedEvent.class));
    }

    /** A Premium waiter must never be handed a Standard seat. */
    @Test
    void neverOffersAcrossCategories() {
        when(entryRepository.lockNextInQueue(eq(showId), eq(standard), any(Pageable.class)))
                .thenReturn(List.of());

        service.assignFreedSeats(cancellation(seat(standard, "Z9")));

        verify(entryRepository, never()).lockNextInQueue(eq(showId), eq(premium), any(Pageable.class));
        verify(offerRepository, never()).save(any());
    }

    /** A multi-seat cancellation should satisfy several single-seat waiters in one pass. */
    @Test
    void cascadesRemainingSeatsToSubsequentWaiters() {
        WaitlistEntry first = waiter(premium, 1, Instant.now().minusSeconds(900));
        WaitlistEntry second = waiter(premium, 1, Instant.now().minusSeconds(600));

        when(entryRepository.lockNextInQueue(eq(showId), eq(premium), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        service.assignFreedSeats(cancellation(seat(premium, "A1"), seat(premium, "A2")));

        verify(offerRepository, times(2)).save(any(SeatOffer.class));
        assertThat(first.getStatus()).isEqualTo(WaitlistStatus.OFFERED);
        assertThat(second.getStatus()).isEqualTo(WaitlistStatus.OFFERED);
    }

    @Test
    void emptyQueueSimplyReturnsSeatsToGeneralAvailability() {
        when(entryRepository.lockNextInQueue(any(), any(), any(Pageable.class))).thenReturn(List.of());

        service.assignFreedSeats(cancellation(seat(premium, "A1")));

        verify(offerRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(WaitlistOfferCreatedEvent.class));
    }
}
