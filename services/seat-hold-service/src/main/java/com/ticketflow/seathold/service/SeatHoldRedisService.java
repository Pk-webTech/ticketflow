package com.ticketflow.seathold.service;

import com.ticketflow.seathold.config.RedisConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * All seat-hold state lives in Redis, keyed as {@code seat:hold:<showId>:<seatId>}
 * with value {@code <holdId>|<customerId>} and a TTL. Two properties matter
 * for correctness:
 *
 * <p><b>Concurrency safety</b> — acquiring a hold for N seats must be
 * all-or-nothing: if two customers race for the same seat, exactly one must
 * win. A per-seat SETNX is atomic, but doing N of them in a loop from the
 * application is NOT atomic across the whole set (another request could
 * interleave). We use a Lua script instead: Redis executes it single-threaded,
 * so the "check every seat is free, then set every seat" sequence is
 * indivisible from the point of view of every other client.
 *
 * <p><b>TTL auto-release</b> — we never write our own expiry scheduler.
 * Redis's own key expiry does the work; {@code SeatMapRedisSubscriber}
 * listens for the expired-key keyspace notification and broadcasts it.
 */
@Service
public class SeatHoldRedisService {

    public static final String HOLD_KEY_PREFIX = "seat:hold:";

    private final StringRedisTemplate redisTemplate;
    private final long holdTtlSeconds;

    private static final DefaultRedisScript<List> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            """
            -- KEYS = one entry per seat key to hold
            -- ARGV[1] = value to set (holdId|customerId)
            -- ARGV[2] = TTL in seconds
            for i = 1, #KEYS do
                if redis.call('EXISTS', KEYS[i]) == 1 then
                    return {0, KEYS[i]}
                end
            end
            for i = 1, #KEYS do
                redis.call('SET', KEYS[i], ARGV[1], 'EX', ARGV[2])
            end
            return {1, ''}
            """,
            List.class
    );

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            local released = 0
            for i = 1, #KEYS do
                local val = redis.call('GET', KEYS[i])
                if val and string.sub(val, 1, string.len(ARGV[1])) == ARGV[1] then
                    redis.call('DEL', KEYS[i])
                    released = released + 1
                end
            end
            return released
            """,
            Long.class
    );

    public SeatHoldRedisService(
            StringRedisTemplate redisTemplate,
            @Value("${seat-hold.ttl-seconds}") long holdTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.holdTtlSeconds = holdTtlSeconds;
    }

    public long getHoldTtlSeconds() {
        return holdTtlSeconds;
    }

    private String seatKey(UUID showId, UUID seatId) {
        return HOLD_KEY_PREFIX + showId + ":" + seatId;
    }

    /**
     * Attempts to atomically hold every seat in {@code seatIds}. Returns the
     * blocking seat's id if any seat was already taken (nothing is written
     * in that case); returns {@code null} on success.
     */
    @SuppressWarnings("unchecked")
    public UUID tryAcquireHold(UUID showId, List<UUID> seatIds, UUID holdId, UUID customerId, long ttlSecondsOverride) {
        List<String> keys = seatIds.stream().map(seatId -> seatKey(showId, seatId)).toList();
        String value = holdId + "|" + customerId;
        long ttl = ttlSecondsOverride > 0 ? ttlSecondsOverride : holdTtlSeconds;

        List<Object> result = redisTemplate.execute(ACQUIRE_SCRIPT, keys, value, String.valueOf(ttl));

        long success = ((Number) result.get(0)).longValue();
        if (success == 1) {
            return null; // all seats acquired
        }

        String blockingKey = (String) result.get(1);
        String[] parts = blockingKey.substring(HOLD_KEY_PREFIX.length()).split(":");
        return UUID.fromString(parts[1]);
    }

    /** Releases only the seats that are still owned by this holdId. Returns count released. */
    public long release(UUID showId, List<UUID> seatIds, UUID holdId) {
        List<String> keys = seatIds.stream().map(seatId -> seatKey(showId, seatId)).toList();
        Long released = redisTemplate.execute(RELEASE_SCRIPT, keys, holdId.toString());
        return released == null ? 0 : released;
    }

    /** Returns true if the given seat currently has an active hold (any customer). */
    public boolean isHeld(UUID showId, UUID seatId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(seatKey(showId, seatId)));
    }

    /** Returns the customerId that holds this seat, or null if unheld. */
    public UUID getHoldingCustomer(UUID showId, UUID seatId) {
        String value = redisTemplate.opsForValue().get(seatKey(showId, seatId));
        if (value == null) return null;
        String[] parts = value.split("\\|");
        return parts.length == 2 ? UUID.fromString(parts[1]) : null;
    }

    public Duration getRemainingTtl(UUID showId, UUID seatId) {
        Long seconds = redisTemplate.getExpire(seatKey(showId, seatId));
        return seconds == null || seconds < 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
    }

    /** Reserved for future use if we add secondary indices (e.g. holdId -> [seatIds]). */
    public void cleanUpExpiredHoldMetadata(UUID showId, UUID seatId) {
        // The Redis key itself is already gone (that's what triggered the expiry event).
    }

    public void publishSeatMapEvent(UUID showId, String jsonPayload) {
        redisTemplate.convertAndSend(RedisConfig.SEATMAP_CHANNEL_PREFIX + showId, jsonPayload);
    }
}
