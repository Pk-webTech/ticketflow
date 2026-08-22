package com.ticketflow.booking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row per seat in a booking.
 *
 * <p>{@link #active} is the field the partial unique index
 * {@code uq_active_seat_per_show (show_id, seat_id) WHERE active} keys off.
 * Confirming sets it true; cancelling sets it false, which both frees the
 * seat for rebooking and preserves the historical row (we never delete
 * booking history).
 */
@Entity
@Table(name = "booking_seats", schema = "booking")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSeat {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Column(name = "seat_label", nullable = false, length = 20)
    private String seatLabel;

    @Column(name = "row_label", length = 10)
    private String rowLabel;

    @Column(name = "seat_number")
    private Integer seatNumber;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "category_name", length = 100)
    private String categoryName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
