package com.ticketflow.waitlist.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A time-limited claim on specific freed seats, offered to one waitlisted
 * customer at a time.
 *
 * <p>{@link #token} is the security boundary: it is emailed inside the claim
 * link and is the ONLY thing needed to view the offer, so it must be
 * high-entropy and single-use. Accepting flips the status, which makes replay
 * of the same link a no-op.
 */
@Entity
@Table(name = "seat_offers", schema = "waitlist")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatOffer {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "waitlist_entry_id", nullable = false)
    private UUID waitlistEntryId;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "seat_ids", nullable = false, columnDefinition = "TEXT")
    private String seatIds;

    @Column(name = "seat_labels", columnDefinition = "TEXT")
    private String seatLabels;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferStatus status;

    @Column(name = "offered_at", nullable = false, updatable = false)
    private Instant offeredAt;

    /** The deadline. Past this, the sweeper reclaims the seats for the next in line. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (offeredAt == null) offeredAt = Instant.now();
    }

    public List<UUID> seatIdList() {
        return Arrays.stream(seatIds.split(",")).filter(s -> !s.isBlank())
                .map(String::trim).map(UUID::fromString).collect(Collectors.toList());
    }
}
