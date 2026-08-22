package com.ticketflow.waitlist.service;

import com.ticketflow.waitlist.entity.*;
import com.ticketflow.waitlist.messaging.BookingCancelledEvent;
import com.ticketflow.waitlist.messaging.WaitlistOfferCreatedEvent;
import com.ticketflow.waitlist.repository.SeatOfferRepository;
import com.ticketflow.waitlist.repository.WaitlistEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The heart of the waitlist feature: turning freed seats into offers, and
 * cascading those offers down the queue when they lapse.
 *
 * <h3>Auto-assignment on cancellation</h3>
 * A {@code booking.cancelled} event arrives carrying the freed seats. Seats
 * are grouped by {@code categoryId} — a Premium waiter must never be offered
 * a Standard seat — and each group is offered to the head of that category's
 * queue.
 *
 * <h3>Why the head of the queue is locked</h3>
 * {@code lockNextInQueue} uses {@code SELECT ... FOR UPDATE SKIP LOCKED}. Two
 * cancellations for the same show and category can be processed concurrently
 * by two instances; without the lock both would read the same "next" row and
 * offer the same person two different seat sets while the person behind them
 * gets nothing. With SKIP LOCKED, the second consumer transparently takes the
 * second person in line. Nobody blocks, nobody is double-offered.
 *
 * <h3>Fan-out granularity</h3>
 * An offer covers up to the waiter's requested {@code quantity}. If a
 * cancellation frees more seats in a category than the head of the queue
 * wants, the remainder immediately cascades to the next waiter in the same
 * pass, so a 4-seat cancellation can satisfy four separate single-seat
 * waiters at once.
 */
@Service
public class WaitlistAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistAssignmentService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WaitlistEntryRepository entryRepository;
    private final SeatOfferRepository offerRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final long offerTtlSeconds;
    private final String publicBaseUrl;

    public WaitlistAssignmentService(WaitlistEntryRepository entryRepository,
                                     SeatOfferRepository offerRepository,
                                     ApplicationEventPublisher eventPublisher,
                                     @Value("${waitlist.offer.ttl-seconds}") long offerTtlSeconds,
                                     @Value("${waitlist.public-base-url}") String publicBaseUrl) {
        this.entryRepository = entryRepository;
        this.offerRepository = offerRepository;
        this.eventPublisher = eventPublisher;
        this.offerTtlSeconds = offerTtlSeconds;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional
    public void assignFreedSeats(BookingCancelledEvent event) {
        if (event.freedSeats() == null || event.freedSeats().isEmpty()) {
            return;
        }

        Map<UUID, List<BookingCancelledEvent.FreedSeat>> byCategory = event.freedSeats().stream()
                .filter(seat -> seat.categoryId() != null)
                .collect(Collectors.groupingBy(BookingCancelledEvent.FreedSeat::categoryId,
                        LinkedHashMap::new, Collectors.toList()));

        byCategory.forEach((categoryId, seats) ->
                offerSeatsToQueue(event.showId(), categoryId, seats, event));
    }

    /** Walks the queue, handing out offers until the freed seats run out or the queue empties. */
    private void offerSeatsToQueue(UUID showId,
                                   UUID categoryId,
                                   List<BookingCancelledEvent.FreedSeat> freed,
                                   BookingCancelledEvent context) {

        Deque<BookingCancelledEvent.FreedSeat> pool = new ArrayDeque<>(freed);
        Set<UUID> alreadyOffered = new HashSet<>();

        while (!pool.isEmpty()) {
            Optional<WaitlistEntry> next = lockNext(showId, categoryId, alreadyOffered);
            if (next.isEmpty()) {
                log.info("No one waiting in category {} for show {} — {} seat(s) simply return to general availability",
                        categoryId, showId, pool.size());
                return;
            }

            WaitlistEntry entry = next.get();
            alreadyOffered.add(entry.getId());

            int take = Math.min(entry.getQuantity(), pool.size());
            List<BookingCancelledEvent.FreedSeat> allocation = new ArrayList<>();
            for (int i = 0; i < take; i++) {
                allocation.add(pool.poll());
            }

            createOffer(entry, allocation, context);
        }
    }

    private Optional<WaitlistEntry> lockNext(UUID showId, UUID categoryId, Set<UUID> skip) {
        // Fetch a small window so we can step past entries already served in this same pass.
        List<WaitlistEntry> candidates =
                entryRepository.lockNextInQueue(showId, categoryId, PageRequest.of(0, 10));
        return candidates.stream().filter(e -> !skip.contains(e.getId())).findFirst();
    }

    private void createOffer(WaitlistEntry entry,
                             List<BookingCancelledEvent.FreedSeat> seats,
                             BookingCancelledEvent context) {

        Instant expiresAt = Instant.now().plusSeconds(offerTtlSeconds);
        String token = generateToken();

        SeatOffer offer = SeatOffer.builder()
                .id(UUID.randomUUID())
                .waitlistEntryId(entry.getId())
                .showId(entry.getShowId())
                .categoryId(entry.getCategoryId())
                .seatIds(seats.stream().map(s -> s.seatId().toString()).collect(Collectors.joining(",")))
                .seatLabels(seats.stream().map(BookingCancelledEvent.FreedSeat::seatLabel)
                        .filter(Objects::nonNull).collect(Collectors.joining(",")))
                .token(token)
                .status(OfferStatus.PENDING)
                .expiresAt(expiresAt)
                .build();

        offerRepository.save(offer);

        // OFFERED takes the entry out of the queue without losing its place:
        // if the offer lapses, WaitlistOfferSweeper decides whether it returns.
        entry.setStatus(WaitlistStatus.OFFERED);
        entryRepository.save(entry);

        eventPublisher.publishEvent(new WaitlistOfferCreatedEvent(
                offer.getId(), entry.getId(), entry.getShowId(), entry.getCategoryId(), entry.getCategoryName(),
                entry.getCustomerId(), entry.getCustomerEmail(), entry.getCustomerName(),
                context.eventTitle(), context.venueName(), context.showStartsAt(),
                seats.stream().map(BookingCancelledEvent.FreedSeat::seatLabel).toList(),
                publicBaseUrl + "/waitlist/offer/" + token,
                expiresAt, offerTtlSeconds, Instant.now()
        ));

        log.info("Offered {} seat(s) in category {} to customer {} — expires {}",
                seats.size(), entry.getCategoryId(), entry.getCustomerId(), expiresAt);
    }

    /**
     * Re-offers the seats from a lapsed offer to the next person in line.
     *
     * <p>{@code REQUIRES_NEW} so that one problematic re-offer cannot roll
     * back the sweeper's entire batch — each expiry is settled independently.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cascadeExpiredOffer(SeatOffer expired, BookingCancelledEvent syntheticContext) {
        List<BookingCancelledEvent.FreedSeat> seats = expired.seatIdList().stream()
                .map(seatId -> new BookingCancelledEvent.FreedSeat(
                        seatId, null, expired.getCategoryId(), null, null))
                .toList();

        offerSeatsToQueue(expired.getShowId(), expired.getCategoryId(), seats, syntheticContext);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
