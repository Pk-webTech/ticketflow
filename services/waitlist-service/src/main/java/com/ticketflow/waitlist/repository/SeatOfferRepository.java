package com.ticketflow.waitlist.repository;

import com.ticketflow.waitlist.entity.OfferStatus;
import com.ticketflow.waitlist.entity.SeatOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatOfferRepository extends JpaRepository<SeatOffer, UUID> {

    Optional<SeatOffer> findByToken(String token);

    /** The sweeper's query: offers whose deadline has passed but are still PENDING. */
    List<SeatOffer> findByStatusAndExpiresAtBefore(OfferStatus status, Instant cutoff);

    List<SeatOffer> findByWaitlistEntryIdOrderByOfferedAtDesc(UUID waitlistEntryId);
}
