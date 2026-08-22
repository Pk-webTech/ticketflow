package com.ticketflow.seathold.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable audit trail of hold lifecycle events. Redis is the source of truth
 * for whether a hold is CURRENTLY active (via TTL); this table answers
 * "what happened" after the fact — useful for support, fraud review, and
 * reconciling booking-service's CONVERTED transition.
 */
@Entity
@Table(name = "hold_audit", schema = "seat_hold")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldAudit {

    @Id
    @Column(name = "hold_id", updatable = false, nullable = false)
    private UUID holdId;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Comma-separated seat UUIDs — simplest durable representation; live detail stays in Redis while active. */
    @Column(name = "seat_ids", nullable = false, columnDefinition = "TEXT")
    private String seatIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldStatus status;

    @Column(name = "ttl_seconds", nullable = false)
    private long ttlSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
