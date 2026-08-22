package com.ticketflow.waitlist.service;

import com.ticketflow.waitlist.dto.JoinWaitlistRequest;
import com.ticketflow.waitlist.dto.OfferResponse;
import com.ticketflow.waitlist.dto.WaitlistEntryResponse;
import com.ticketflow.waitlist.entity.*;
import com.ticketflow.waitlist.exception.WaitlistExceptions;
import com.ticketflow.waitlist.messaging.WaitlistOfferAcceptedEvent;
import com.ticketflow.waitlist.repository.SeatOfferRepository;
import com.ticketflow.waitlist.repository.WaitlistEntryRepository;
import com.ticketflow.waitlist.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Customer-facing waitlist operations: join, view, leave, and claim an offer. */
@Service
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitlistEntryRepository entryRepository;
    private final SeatOfferRepository offerRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WaitlistService(WaitlistEntryRepository entryRepository,
                           SeatOfferRepository offerRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.entryRepository = entryRepository;
        this.offerRepository = offerRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public WaitlistEntryResponse join(JoinWaitlistRequest request, AuthenticatedUser customer) {
        entryRepository.findByShowIdAndCategoryIdAndCustomerIdAndStatusIn(
                request.showId(), request.categoryId(), customer.userId(),
                List.of(WaitlistStatus.ACTIVE, WaitlistStatus.OFFERED)
        ).ifPresent(existing -> {
            throw new WaitlistExceptions.AlreadyOnWaitlistException();
        });

        WaitlistEntry entry = WaitlistEntry.builder()
                .id(UUID.randomUUID())
                .showId(request.showId())
                .eventId(request.eventId())
                .categoryId(request.categoryId())
                .categoryName(request.categoryName())
                .customerId(customer.userId())
                .customerEmail(customer.email())
                .customerName(request.customerName() == null ? customer.email() : request.customerName())
                .quantity(Math.max(1, request.quantity()))
                .status(WaitlistStatus.ACTIVE)
                .build();

        try {
            entryRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException ex) {
            // uq_waitlist_live_entry — a concurrent duplicate join from the same customer.
            throw new WaitlistExceptions.AlreadyOnWaitlistException();
        }

        log.info("Customer {} joined waitlist for show {} category {}",
                customer.userId(), request.showId(), request.categoryId());

        return WaitlistEntryResponse.from(entry, positionOf(entry));
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntryResponse> myEntries(UUID customerId) {
        return entryRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(entry -> WaitlistEntryResponse.from(entry, positionOf(entry)))
                .toList();
    }

    @Transactional
    public void leave(UUID entryId, AuthenticatedUser customer) {
        WaitlistEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new WaitlistExceptions.EntryNotFoundException(entryId));

        if (!entry.getCustomerId().equals(customer.userId())) {
            throw new WaitlistExceptions.NotEntryOwnerException();
        }

        entry.setStatus(WaitlistStatus.CANCELLED);
        entryRepository.save(entry);
    }

    /**
     * Opening the emailed claim link. Deliberately readable without a JWT —
     * the token is the credential — but the deadline is re-checked here so a
     * stale link is rejected even before the sweeper has run.
     */
    @Transactional(readOnly = true)
    public OfferResponse viewOffer(String token) {
        SeatOffer offer = offerRepository.findByToken(token)
                .orElseThrow(WaitlistExceptions::new_offerNotFound);

        if (offer.getStatus() == OfferStatus.PENDING && offer.getExpiresAt().isBefore(Instant.now())) {
            throw new WaitlistExceptions.OfferExpiredException();
        }
        return OfferResponse.from(offer);
    }

    /**
     * Accepting an offer. Publishes {@code waitlist.offer.accepted}, which
     * seat-hold-service turns into a normal TTL hold — from there the customer
     * completes checkout through the standard booking flow, so there is no
     * second, separately-secured purchase path to maintain.
     */
    @Transactional
    public OfferResponse acceptOffer(String token, AuthenticatedUser customer) {
        SeatOffer offer = offerRepository.findByToken(token)
                .orElseThrow(WaitlistExceptions::new_offerNotFound);

        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new WaitlistExceptions.OfferAlreadyResolvedException();
        }
        if (offer.getExpiresAt().isBefore(Instant.now())) {
            throw new WaitlistExceptions.OfferExpiredException();
        }

        WaitlistEntry entry = entryRepository.findById(offer.getWaitlistEntryId())
                .orElseThrow(() -> new WaitlistExceptions.EntryNotFoundException(offer.getWaitlistEntryId()));

        if (!entry.getCustomerId().equals(customer.userId())) {
            throw new WaitlistExceptions.NotEntryOwnerException();
        }

        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setResolvedAt(Instant.now());
        offerRepository.save(offer);

        entry.setStatus(WaitlistStatus.CONVERTED);
        entryRepository.save(entry);

        eventPublisher.publishEvent(new WaitlistOfferAcceptedEvent(
                offer.getId(), offer.getShowId(), entry.getCustomerId(), offer.seatIdList()));

        log.info("Offer {} accepted by customer {} — converting to seat hold", offer.getId(), customer.userId());

        return OfferResponse.from(offer);
    }

    @Transactional(readOnly = true)
    public long queueLength(UUID showId, UUID categoryId) {
        return entryRepository.countByShowIdAndCategoryIdAndStatus(showId, categoryId, WaitlistStatus.ACTIVE);
    }

    private Long positionOf(WaitlistEntry entry) {
        if (entry.getStatus() != WaitlistStatus.ACTIVE) {
            return null;
        }
        return entryRepository.countAhead(entry.getShowId(), entry.getCategoryId(), entry.getCreatedAt()) + 1;
    }
}
