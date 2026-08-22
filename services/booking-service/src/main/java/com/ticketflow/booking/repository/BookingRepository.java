package com.ticketflow.booking.repository;

import com.ticketflow.booking.entity.Booking;
import com.ticketflow.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = "seats")
    Optional<Booking> findByBookingReference(String bookingReference);

    @EntityGraph(attributePaths = "seats")
    Optional<Booking> findWithSeatsById(UUID id);

    @EntityGraph(attributePaths = "seats")
    Page<Booking> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    @EntityGraph(attributePaths = "seats")
    List<Booking> findByShowIdAndStatus(UUID showId, BookingStatus status);

    long countByShowIdAndStatus(UUID showId, BookingStatus status);

    @Query("""
           SELECT COALESCE(SUM(b.totalAmount), 0)
           FROM Booking b
           WHERE b.showId = :showId AND b.status = 'CONFIRMED'
           """)
    BigDecimal sumConfirmedRevenueForShow(@Param("showId") UUID showId);

    @Query("""
           SELECT COALESCE(SUM(b.totalAmount), 0)
           FROM Booking b
           WHERE b.eventId = :eventId AND b.status = 'CONFIRMED'
           """)
    BigDecimal sumConfirmedRevenueForEvent(@Param("eventId") UUID eventId);

    @EntityGraph(attributePaths = "seats")
    List<Booking> findByEventIdAndStatus(UUID eventId, BookingStatus status);
}
