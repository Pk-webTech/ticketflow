package com.ticketflow.booking.service;

import com.ticketflow.booking.client.SeatHoldClient;
import com.ticketflow.booking.client.EventDetailsDto;
import com.ticketflow.booking.client.ShowDetailsDto;
import com.ticketflow.booking.client.VenueDetailsDto;
import com.ticketflow.booking.client.VenueEventClient;
import com.ticketflow.booking.client.VenueSeatDto;
import com.ticketflow.booking.dto.BookingResponse;
import com.ticketflow.booking.dto.CreateBookingRequest;
import com.ticketflow.booking.entity.Booking;
import com.ticketflow.booking.entity.BookingStatus;
import com.ticketflow.booking.exception.BookingExceptions;
import com.ticketflow.booking.repository.BookingRepository;
import com.ticketflow.booking.repository.BookingSeatRepository;
import com.ticketflow.booking.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingSeatRepository bookingSeatRepository;
    @Mock HoldVerificationService holdVerificationService;
    @Mock BookingReferenceGenerator referenceGenerator;
    @Mock VenueEventClient venueEventClient;
    @Mock SeatHoldClient seatHoldClient;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks BookingService bookingService;

    private final UUID showId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID venueId = UUID.randomUUID();
    private final UUID seatId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID holdId = UUID.randomUUID();

    private AuthenticatedUser customer;

    @BeforeEach
    void setUp() {
        customer = new AuthenticatedUser(UUID.randomUUID(), "alice@example.com", "CUSTOMER");

        when(referenceGenerator.generate()).thenReturn("TF-ABCD1234");
        when(venueEventClient.getShow(showId)).thenReturn(new ShowDetailsDto(
                showId, eventId, venueId, Instant.now().plus(2, ChronoUnit.DAYS), "SCHEDULED",
                List.of(new ShowDetailsDto.CategoryPriceView(categoryId, "Premium", new BigDecimal("450.00")))
        ));
        when(venueEventClient.getVenueSeats(venueId)).thenReturn(List.of(
                new VenueSeatDto(seatId, venueId, categoryId, "A", 12, "MAIN")
        ));
        when(venueEventClient.getEventOrNull(eventId))
                .thenReturn(new EventDetailsDto(eventId, "Interstellar", "MOVIE", null));
        when(venueEventClient.getVenueOrNull(venueId))
                .thenReturn(new VenueDetailsDto(venueId, "PVR Grand", "Chennai", null));
    }

    private CreateBookingRequest request() {
        return new CreateBookingRequest(showId, holdId, List.of(seatId), "Alice");
    }

    @Test
    void confirmsBookingAndPricesSeatsFromShowPricing() {
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.confirm(request(), customer, "Bearer token");

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.bookingReference()).isEqualTo("TF-ABCD1234");
        assertThat(response.totalAmount()).isEqualByComparingTo("450.00");
        assertThat(response.seats()).singleElement()
                .satisfies(seat -> {
                    assertThat(seat.seatLabel()).isEqualTo("A12");
                    assertThat(seat.categoryName()).isEqualTo("Premium");
                });

        // hold must be checked BEFORE we touch the database
        verify(holdVerificationService).assertHoldValid(showId, List.of(seatId), holdId, customer.userId());
        verify(seatHoldClient).markConverted(eq(showId), eq(holdId), eq(List.of(seatId)), eq(customer.userId()), anyString());
    }

    @Test
    void expiredHoldIsRejectedBeforeAnyDatabaseWrite() {
        doThrow(new BookingExceptions.HoldExpiredException(seatId))
                .when(holdVerificationService).assertHoldValid(any(), any(), any(), any());

        assertThatThrownBy(() -> bookingService.confirm(request(), customer, "Bearer token"))
                .isInstanceOf(BookingExceptions.HoldExpiredException.class);

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    /**
     * The critical concurrency case: two customers race, Redis lets both
     * through (however unlikely), and the partial unique index rejects the
     * loser. That MUST surface as a clean 409, not a 500.
     */
    @Test
    void uniqueIndexViolationBecomesSeatAlreadyBooked() {
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates uq_active_seat_per_show"));

        assertThatThrownBy(() -> bookingService.confirm(request(), customer, "Bearer token"))
                .isInstanceOf(BookingExceptions.SeatAlreadyBookedException.class);

        // no ticket email may be emitted for a booking that never committed
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void cancellingDeactivatesSeatsSoTheyCanBeRebooked() {
        Booking booking = existingBooking(BookingStatus.CONFIRMED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(bookingRepository.findWithSeatsById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.cancel(booking.getId(), customer);

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getSeats()).allSatisfy(seat -> assertThat(seat.isActive()).isFalse());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void cannotCancelSomeoneElsesBooking() {
        Booking booking = existingBooking(BookingStatus.CONFIRMED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(bookingRepository.findWithSeatsById(booking.getId())).thenReturn(Optional.of(booking));

        AuthenticatedUser stranger = new AuthenticatedUser(UUID.randomUUID(), "bob@example.com", "CUSTOMER");

        assertThatThrownBy(() -> bookingService.cancel(booking.getId(), stranger))
                .isInstanceOf(BookingExceptions.NotBookingOwnerException.class);
    }

    @Test
    void cannotCancelTwice() {
        Booking booking = existingBooking(BookingStatus.CANCELLED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(bookingRepository.findWithSeatsById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(booking.getId(), customer))
                .isInstanceOf(BookingExceptions.BookingAlreadyCancelledException.class);
    }

    @Test
    void cannotCancelAfterShowHasStarted() {
        Booking booking = existingBooking(BookingStatus.CONFIRMED, Instant.now().minus(1, ChronoUnit.HOURS));
        when(bookingRepository.findWithSeatsById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(booking.getId(), customer))
                .isInstanceOf(BookingExceptions.CancellationWindowClosedException.class);
    }

    private Booking existingBooking(BookingStatus status, Instant startsAt) {
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .bookingReference("TF-EXIST001")
                .showId(showId)
                .eventId(eventId)
                .holdId(holdId)
                .customerId(customer.userId())
                .customerEmail(customer.email())
                .customerName("Alice")
                .status(status)
                .totalAmount(new BigDecimal("450.00"))
                .showStartsAt(startsAt)
                .build();

        booking.addSeat(com.ticketflow.booking.entity.BookingSeat.builder()
                .id(UUID.randomUUID())
                .showId(showId)
                .seatId(seatId)
                .seatLabel("A12")
                .rowLabel("A")
                .seatNumber(12)
                .categoryId(categoryId)
                .categoryName("Premium")
                .price(new BigDecimal("450.00"))
                .active(status == BookingStatus.CONFIRMED)
                .build());

        return booking;
    }
}
