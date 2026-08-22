package com.ticketflow.waitlist.dto;

import com.ticketflow.waitlist.entity.OfferStatus;
import com.ticketflow.waitlist.entity.SeatOffer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OfferResponse(
        UUID offerId,
        UUID showId,
        UUID categoryId,
        List<UUID> seatIds,
        List<String> seatLabels,
        OfferStatus status,
        Instant expiresAt,
        /** Drives the countdown timer on the claim page. */
        long secondsRemaining
) {
    public static OfferResponse from(SeatOffer offer) {
        long remaining = Math.max(0, Duration.between(Instant.now(), offer.getExpiresAt()).getSeconds());
        List<String> labels = offer.getSeatLabels() == null
                ? List.of()
                : List.of(offer.getSeatLabels().split(","));
        return new OfferResponse(offer.getId(), offer.getShowId(), offer.getCategoryId(),
                offer.seatIdList(), labels, offer.getStatus(), offer.getExpiresAt(), remaining);
    }
}
