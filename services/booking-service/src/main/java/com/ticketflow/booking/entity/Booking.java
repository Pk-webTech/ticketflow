package com.ticketflow.booking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings", schema = "booking")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * Human-facing reference (e.g. {@code TF-8F3K2QD1}). This — not the UUID
     * primary key — is what the QR code encodes and what gate staff scan.
     */
    @Column(name = "booking_reference", nullable = false, unique = true, length = 20)
    private String bookingReference;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** The seat-hold this booking was converted from (null for direct/admin bookings). */
    @Column(name = "hold_id")
    private UUID holdId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** Denormalised show/event detail so tickets and history render without cross-service calls. */
    @Column(name = "event_title")
    private String eventTitle;

    @Column(name = "venue_name")
    private String venueName;

    @Column(name = "show_starts_at")
    private Instant showStartsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /**
     * LAZY on purpose. Paginated history queries with an EAGER collection force
     * Hibernate into in-memory pagination (HHH000104); the read paths use
     * {@code @EntityGraph} to fetch seats in one join instead.
     */
    @Builder.Default
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BookingSeat> seats = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (id == null) id = UUID.randomUUID();
    }

    public void addSeat(BookingSeat seat) {
        seat.setBooking(this);
        this.seats.add(seat);
    }
}
