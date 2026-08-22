package com.ticketflow.booking.service;

import com.ticketflow.booking.exception.BookingExceptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Verifies, at checkout time, that the customer still legitimately holds
 * every seat they're trying to book.
 *
 * <p>Reads the same Redis keys seat-hold-service writes
 * ({@code seat:hold:<showId>:<seatId>} = {@code <holdId>|<customerId>}).
 * This is a READ-ONLY consumer of that keyspace — booking-service never
 * writes or deletes hold keys; releasing is always seat-hold-service's job
 * via the /convert callback. Sharing the key format is a deliberate,
 * documented coupling: an HTTP round-trip per seat would add latency to the
 * single most contended path in the system.
 *
 * <p>Note this check is NOT the concurrency guarantee — it's a fast, friendly
 * pre-check that produces a clear 409 ("your hold expired") instead of an
 * opaque database error. The actual guarantee is the partial unique index
 * {@code uq_active_seat_per_show}. A hold could in principle expire in the
 * microseconds between this check and the insert; the index still catches it.
 */
@Service
public class HoldVerificationService {

    private static final String HOLD_KEY_PREFIX = "seat:hold:";

    private final StringRedisTemplate redisTemplate;

    public HoldVerificationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @throws BookingExceptions.HoldExpiredException  if any seat's hold has lapsed
     * @throws BookingExceptions.NotHoldOwnerException if a seat is held by someone else
     */
    public void assertHoldValid(UUID showId, List<UUID> seatIds, UUID holdId, UUID customerId) {
        for (UUID seatId : seatIds) {
            String value = redisTemplate.opsForValue().get(HOLD_KEY_PREFIX + showId + ":" + seatId);

            if (value == null) {
                throw new BookingExceptions.HoldExpiredException(seatId);
            }

            String[] parts = value.split("\\|");
            if (parts.length != 2) {
                throw new BookingExceptions.HoldExpiredException(seatId);
            }

            UUID heldByHold = UUID.fromString(parts[0]);
            UUID heldByCustomer = UUID.fromString(parts[1]);

            if (!heldByCustomer.equals(customerId) || !heldByHold.equals(holdId)) {
                throw new BookingExceptions.NotHoldOwnerException();
            }
        }
    }
}
