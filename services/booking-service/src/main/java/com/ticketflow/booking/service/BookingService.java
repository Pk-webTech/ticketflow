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
import com.ticketflow.booking.entity.BookingSeat;
import com.ticketflow.booking.entity.BookingStatus;
import com.ticketflow.booking.exception.BookingExceptions;
import com.ticketflow.booking.messaging.BookingCancelledEvent;
import com.ticketflow.booking.messaging.BookingConfirmedEvent;
import com.ticketflow.booking.repository.BookingRepository;
import com.ticketflow.booking.repository.BookingSeatRepository;
import com.ticketflow.booking.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Booking lifecycle. The two operations that carry real design weight:
 *
 * <p><b>confirm()</b> — turns a live Redis hold into a permanent booking.
 * Three layers of protection, in order:
 * <ol>
 *   <li>Redis hold check ({@link HoldVerificationService}) — fast, gives a
 *       friendly error, and is what stops 99.9% of double-booking attempts
 *       before they reach the database.</li>
 *   <li>Partial unique index {@code uq_active_seat_per_show} — the actual
 *       guarantee. Two concurrent transactions inserting the same
 *       (show_id, seat_id) cannot both commit; the loser gets a
 *       {@link DataIntegrityViolationException} which we translate to 409.</li>
 *   <li>Transactional event publishing — the QR-ticket email is only emitted
 *       after the transaction commits, so a rolled-back race never produces
 *       a ticket for a booking that doesn't exist.</li>
 * </ol>
 *
 * <p><b>cancel()</b> — flips the booking to CANCELLED and, critically, sets
 * every seat row's {@code active = false}. That single flag both releases the
 * seat under the unique index (so it can be rebooked) and preserves the
 * historical record. The resulting {@code booking.cancelled} event is what
 * kicks off waitlist auto-assignment downstream.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final HoldVerificationService holdVerificationService;
    private final BookingReferenceGenerator referenceGenerator;
    private final VenueEventClient venueEventClient;
    private final SeatHoldClient seatHoldClient;
    private final ApplicationEventPublisher eventPublisher;

    public BookingService(BookingRepository bookingRepository,
                          BookingSeatRepository bookingSeatRepository,
                          HoldVerificationService holdVerificationService,
                          BookingReferenceGenerator referenceGenerator,
                          VenueEventClient venueEventClient,
                          SeatHoldClient seatHoldClient,
                          ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.holdVerificationService = holdVerificationService;
        this.referenceGenerator = referenceGenerator;
        this.venueEventClient = venueEventClient;
        this.seatHoldClient = seatHoldClient;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------
    // Confirm
    // ------------------------------------------------------------------

    @Transactional
    public BookingResponse confirm(CreateBookingRequest request, AuthenticatedUser customer, String bearerToken) {

        // Layer 1 — is the hold still alive, and is it actually theirs?
        holdVerificationService.assertHoldValid(
                request.showId(), request.seatIds(), request.holdId(), customer.userId());

        ShowDetailsDto show = venueEventClient.getShow(request.showId());
        Map<UUID, VenueSeatDto> seatIndex = venueEventClient.getVenueSeats(show.venueId()).stream()
                .collect(Collectors.toMap(VenueSeatDto::id, Function.identity(), (a, b) -> a));
        Map<UUID, ShowDetailsDto.CategoryPriceView> priceByCategory = show.pricing() == null
                ? Map.of()
                : show.pricing().stream()
                .collect(Collectors.toMap(ShowDetailsDto.CategoryPriceView::categoryId, Function.identity(), (a, b) -> a));

        // Cosmetic metadata for the ticket — never allowed to fail the booking.
        EventDetailsDto event = venueEventClient.getEventOrNull(show.eventId());
        VenueDetailsDto venue = venueEventClient.getVenueOrNull(show.venueId());

        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .bookingReference(referenceGenerator.generate())
                .showId(request.showId())
                .eventId(show.eventId())
                .holdId(request.holdId())
                .customerId(customer.userId())
                .customerEmail(customer.email())
                .customerName(request.customerName() == null || request.customerName().isBlank()
                        ? customer.email() : request.customerName())
                .status(BookingStatus.CONFIRMED)
                .totalAmount(BigDecimal.ZERO)
                .eventTitle(event == null ? null : event.title())
                .venueName(venue == null ? null : venue.name())
                .showStartsAt(show.showDateTime())
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (UUID seatId : request.seatIds()) {
            VenueSeatDto seat = seatIndex.get(seatId);
            if (seat == null) {
                throw new BookingExceptions.UpstreamServiceException(
                        "venue-event-service", "seat " + seatId + " does not belong to this show's venue");
            }

            ShowDetailsDto.CategoryPriceView pricing = priceByCategory.get(seat.categoryId());
            BigDecimal price = pricing == null ? BigDecimal.ZERO : pricing.price();
            String categoryName = pricing == null ? null : pricing.categoryName();

            booking.addSeat(BookingSeat.builder()
                    .id(UUID.randomUUID())
                    .showId(request.showId())
                    .seatId(seatId)
                    .seatLabel(seat.displayLabel())
                    .rowLabel(seat.rowLabel())
                    .seatNumber(seat.seatNumber())
                    .categoryId(seat.categoryId())
                    .categoryName(categoryName)
                    .price(price)
                    .active(true)
                    .build());

            total = total.add(price);
        }

        booking.setTotalAmount(total);

        // Layer 2 — the database has the final say.
        try {
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent booking rejected by uq_active_seat_per_show for show {}: {}",
                    request.showId(), ex.getMostSpecificCause().getMessage());
            throw new BookingExceptions.SeatAlreadyBookedException();
        }

        // Tell seat-hold-service the seats are now permanently BOOKED, not HELD.
        seatHoldClient.markConverted(
                request.showId(), request.holdId(), request.seatIds(), customer.userId(), bearerToken);

        // Layer 3 — fires only after this transaction commits.
        eventPublisher.publishEvent(toConfirmedEvent(booking));

        log.info("Booking {} confirmed for customer {} ({} seats, total {})",
                booking.getBookingReference(), customer.userId(), booking.getSeats().size(), total);

        return BookingResponse.from(booking);
    }

    // ------------------------------------------------------------------
    // Cancel
    // ------------------------------------------------------------------

    @Transactional
    public BookingResponse cancel(UUID bookingId, AuthenticatedUser user) {
        Booking booking = bookingRepository.findWithSeatsById(bookingId)
                .orElseThrow(() -> new BookingExceptions.BookingNotFoundException(bookingId));

        boolean isPrivileged = "ADMIN".equals(user.role()) || "ORGANISER".equals(user.role());
        if (!booking.getCustomerId().equals(user.userId()) && !isPrivileged) {
            throw new BookingExceptions.NotBookingOwnerException();
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingExceptions.BookingAlreadyCancelledException();
        }

        if (booking.getShowStartsAt() != null && booking.getShowStartsAt().isBefore(Instant.now())) {
            throw new BookingExceptions.CancellationWindowClosedException();
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());

        // Releasing the seats under the unique index without losing history.
        booking.getSeats().forEach(seat -> seat.setActive(false));

        bookingRepository.saveAndFlush(booking);

        // Downstream: notification-service emails the customer; waitlist-service
        // offers these freed seats to the next person in each category queue.
        eventPublisher.publishEvent(toCancelledEvent(booking));

        log.info("Booking {} cancelled — {} seats returned to the pool for show {}",
                booking.getBookingReference(), booking.getSeats().size(), booking.getShowId());

        return BookingResponse.from(booking);
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<BookingResponse> history(UUID customerId, Pageable pageable) {
        return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(BookingResponse::from);
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(UUID bookingId, AuthenticatedUser user) {
        Booking booking = bookingRepository.findWithSeatsById(bookingId)
                .orElseThrow(() -> new BookingExceptions.BookingNotFoundException(bookingId));

        boolean isPrivileged = "ADMIN".equals(user.role()) || "ORGANISER".equals(user.role());
        if (!booking.getCustomerId().equals(user.userId()) && !isPrivileged) {
            throw new BookingExceptions.NotBookingOwnerException();
        }
        return BookingResponse.from(booking);
    }

    /** Used at the gate: scan the QR → look up by the encoded reference. */
    @Transactional(readOnly = true)
    public BookingResponse getByReference(String reference) {
        return bookingRepository.findByBookingReference(reference)
                .map(BookingResponse::from)
                .orElseThrow(() -> new BookingExceptions.BookingNotFoundException(reference));
    }

    /**
     * Seat IDs permanently taken for a show. seat-hold-service overlays this
     * on the venue layout so the visual map can distinguish BOOKED (never
     * becomes available again on its own) from HELD (will auto-release).
     */
    @Transactional(readOnly = true)
    public List<UUID> bookedSeatIds(UUID showId) {
        return bookingSeatRepository.findActiveSeatIdsForShow(showId);
    }

    // ------------------------------------------------------------------
    // Event mapping
    // ------------------------------------------------------------------

    private BookingConfirmedEvent toConfirmedEvent(Booking b) {
        List<BookingConfirmedEvent.SeatLine> lines = b.getSeats().stream()
                .map(s -> new BookingConfirmedEvent.SeatLine(s.getSeatLabel(), s.getCategoryName(), s.getPrice()))
                .toList();

        return new BookingConfirmedEvent(
                b.getId(), b.getBookingReference(), b.getCustomerId(), b.getCustomerEmail(), b.getCustomerName(),
                b.getShowId(), b.getEventId(), b.getEventTitle(), b.getVenueName(), b.getShowStartsAt(),
                lines, b.getTotalAmount(), Instant.now()
        );
    }

    private BookingCancelledEvent toCancelledEvent(Booking b) {
        List<BookingCancelledEvent.FreedSeat> freed = b.getSeats().stream()
                .map(s -> new BookingCancelledEvent.FreedSeat(
                        s.getSeatId(), s.getSeatLabel(), s.getCategoryId(), s.getCategoryName(), s.getPrice()))
                .toList();

        return new BookingCancelledEvent(
                b.getId(), b.getBookingReference(), b.getCustomerId(), b.getCustomerEmail(), b.getCustomerName(),
                b.getShowId(), b.getEventId(), b.getEventTitle(), b.getVenueName(), b.getShowStartsAt(),
                freed, b.getTotalAmount(), Instant.now()
        );
    }
}
