package com.ticketflow.venueevent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A single physical seat belonging to a venue's layout. This is the static
 * template — live per-show availability (available/held/booked) is tracked
 * separately by seat-hold-service, keyed by (showId, seatId).
 */
@Entity
@Table(
        name = "seats",
        schema = "venue_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_seat_venue_row_number",
                columnNames = {"venue_id", "row_label", "seat_number"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    /** e.g. "A", "B", "C" — a row within a section. */
    @Column(name = "row_label", nullable = false, length = 10)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    /** Optional section grouping for large venues, e.g. "Lower Bowl", "Balcony". */
    @Column(length = 50)
    private String section;
}
