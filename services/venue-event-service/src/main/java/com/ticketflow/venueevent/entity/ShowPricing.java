package com.ticketflow.venueevent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The price charged for a given seat category on a specific show. Lets an
 * organiser price the same venue/category differently per show (e.g. matinee
 * vs. prime time).
 */
@Entity
@Table(
        name = "show_pricing",
        schema = "venue_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_show_pricing_show_category",
                columnNames = {"show_id", "category_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowPricing {

    @Id
    @GeneratedValue
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private BigDecimal price;
}
