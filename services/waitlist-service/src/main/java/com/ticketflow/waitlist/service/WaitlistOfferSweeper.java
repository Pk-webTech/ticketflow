package com.ticketflow.waitlist.service;

import com.ticketflow.waitlist.entity.OfferStatus;
import com.ticketflow.waitlist.entity.SeatOffer;
import com.ticketflow.waitlist.entity.WaitlistEntry;
import com.ticketflow.waitlist.entity.WaitlistStatus;
import com.ticketflow.waitlist.messaging.BookingCancelledEvent;
import com.ticketflow.waitlist.repository.SeatOfferRepository;
import com.ticketflow.waitlist.repository.WaitlistEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Enforces the offer deadline.
 *
 * <h3>Why a scheduler here, and not Redis TTL as with seat holds</h3>
 * Seat holds live in Redis because they're high-frequency, ephemeral, and
 * their expiry must be reflected in a seat map within milliseconds. Waitlist
 * offers are the opposite: low-volume, long-lived (15 minutes by default),
 * and — crucially — their expiry must trigger a *transactional cascade*
 * (mark expired, re-offer to next in line, emit a new email). That work needs
 * the database anyway, and it must survive a service restart. A Redis
 * keyspace notification is fire-and-forget: if no instance is subscribed at
 * the moment it fires, the event is simply lost and the offer would hang
 * PENDING forever. A swept table is self-healing — whatever is overdue gets
 * picked up on the next tick, however long the service was down.
 *
 * <p>The sweep interval therefore only affects *latency* of the cascade, never
 * correctness: the authoritative deadline is the {@code expires_at} column,
 * checked on read as well, so a customer who clicks a stale link is rejected
 * even if the sweeper has not run yet.
 */
@Component
public class WaitlistOfferSweeper {

    private static final Logger log = LoggerFactory.getLogger(WaitlistOfferSweeper.class);

    private final SeatOfferRepository offerRepository;
    private final WaitlistEntryRepository entryRepository;
    private final WaitlistAssignmentService assignmentService;

    public WaitlistOfferSweeper(SeatOfferRepository offerRepository,
                                WaitlistEntryRepository entryRepository,
                                WaitlistAssignmentService assignmentService) {
        this.offerRepository = offerRepository;
        this.entryRepository = entryRepository;
        this.assignmentService = assignmentService;
    }

    @Scheduled(fixedDelayString = "${waitlist.offer.sweep-interval-ms}")
    @Transactional
    public void sweepExpiredOffers() {
        List<SeatOffer> expired =
                offerRepository.findByStatusAndExpiresAtBefore(OfferStatus.PENDING, Instant.now());

        if (expired.isEmpty()) {
            return;
        }

        log.info("Sweeping {} expired waitlist offer(s)", expired.size());

        for (SeatOffer offer : expired) {
            try {
                offer.setStatus(OfferStatus.EXPIRED);
                offer.setResolvedAt(Instant.now());
                offerRepository.save(offer);

                // The waiter who ignored their offer does not silently re-enter the
                // queue — they had their turn. They can rejoin explicitly.
                entryRepository.findById(offer.getWaitlistEntryId()).ifPresent(entry -> {
                    if (entry.getStatus() == WaitlistStatus.OFFERED) {
                        entry.setStatus(WaitlistStatus.EXPIRED);
                        entryRepository.save(entry);
                    }
                });

                assignmentService.cascadeExpiredOffer(offer, syntheticContext(offer));

                log.info("Offer {} expired — seats passed to the next customer in category {}",
                        offer.getId(), offer.getCategoryId());
            } catch (Exception ex) {
                log.error("Failed to cascade expired offer {}: {}", offer.getId(), ex.getMessage());
            }
        }
    }

    /**
     * The original cancellation's event/venue metadata isn't stored on the
     * offer, so re-offer emails carry only what we know for certain. The claim
     * link and seat labels — the parts that matter — are always present.
     */
    private BookingCancelledEvent syntheticContext(SeatOffer offer) {
        return new BookingCancelledEvent(
                null, null, offer.getShowId(), null, null, null, null, List.of(), Instant.now());
    }
}
