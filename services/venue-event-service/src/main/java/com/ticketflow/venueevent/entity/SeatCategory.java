package com.ticketflow.venueevent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A pricing/seating tier within a venue (e.g. "Premium", "Standard", "VIP").
 * Base price here is a venue-level default; actual per-show price is set on
 * {@link ShowPricing} and can differ per show.
 */
@Entity
@Table(name = "seat_categories", schema = "venue_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatCategory {

    @Id
    @GeneratedValue
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(nullable = false, length = 50)
    private String name;

    /** Hex color for frontend seat-map legend, e.g. "#F5A623". */
    @Column(name = "display_color", length = 7)
    private String displayColor;

    @Column(name = "default_price", nullable = false)
    private java.math.BigDecimal defaultPrice;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;
}
