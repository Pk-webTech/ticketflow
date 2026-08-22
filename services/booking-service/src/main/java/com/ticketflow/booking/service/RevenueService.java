package com.ticketflow.booking.service;

import com.ticketflow.booking.dto.RevenueSummaryResponse;
import com.ticketflow.booking.entity.Booking;
import com.ticketflow.booking.entity.BookingSeat;
import com.ticketflow.booking.entity.BookingStatus;
import com.ticketflow.booking.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Organiser-facing booking + revenue reporting.
 *
 * <p>Computed from CONFIRMED bookings only — a cancelled booking contributes
 * to {@code refundedAmount} and to the cancellation count, never to
 * {@code grossRevenue}. Seats sold are counted from ACTIVE seat rows, which
 * means a partially-cancelled show reports the truth without any extra
 * bookkeeping.
 */
@Service
public class RevenueService {

    private final BookingRepository bookingRepository;

    public RevenueService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public RevenueSummaryResponse forShow(UUID showId) {
        List<Booking> confirmed = bookingRepository.findByShowIdAndStatus(showId, BookingStatus.CONFIRMED);
        List<Booking> cancelled = bookingRepository.findByShowIdAndStatus(showId, BookingStatus.CANCELLED);
        return summarise(showId, "SHOW", confirmed, cancelled);
    }

    @Transactional(readOnly = true)
    public RevenueSummaryResponse forEvent(UUID eventId) {
        List<Booking> confirmed = bookingRepository.findByEventIdAndStatus(eventId, BookingStatus.CONFIRMED);
        List<Booking> cancelled = bookingRepository.findByEventIdAndStatus(eventId, BookingStatus.CANCELLED);
        return summarise(eventId, "EVENT", confirmed, cancelled);
    }

    private RevenueSummaryResponse summarise(UUID scopeId, String scope,
                                             List<Booking> confirmed, List<Booking> cancelled) {

        BigDecimal gross = confirmed.stream()
                .map(Booking::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal refunded = cancelled.stream()
                .map(Booking::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long seatsSold = 0;
        Map<UUID, long[]> countByCategory = new LinkedHashMap<>();
        Map<UUID, BigDecimal> revenueByCategory = new LinkedHashMap<>();
        Map<UUID, String> nameByCategory = new LinkedHashMap<>();

        for (Booking booking : confirmed) {
            for (BookingSeat seat : booking.getSeats()) {
                if (!seat.isActive()) continue;
                seatsSold++;
                UUID categoryId = seat.getCategoryId();
                countByCategory.computeIfAbsent(categoryId, k -> new long[1])[0]++;
                revenueByCategory.merge(categoryId, seat.getPrice(), BigDecimal::add);
                nameByCategory.putIfAbsent(categoryId, seat.getCategoryName());
            }
        }

        List<RevenueSummaryResponse.CategoryBreakdown> breakdown = countByCategory.entrySet().stream()
                .map(entry -> new RevenueSummaryResponse.CategoryBreakdown(
                        entry.getKey(),
                        nameByCategory.get(entry.getKey()),
                        entry.getValue()[0],
                        revenueByCategory.getOrDefault(entry.getKey(), BigDecimal.ZERO)))
                .toList();

        return new RevenueSummaryResponse(
                scopeId,
                scope,
                confirmed.size(),
                cancelled.size(),
                seatsSold,
                gross,
                refunded,
                gross,   // net == gross: refunds are already excluded from `confirmed`
                breakdown
        );
    }
}
