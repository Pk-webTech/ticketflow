package com.ticketflow.seathold.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.seathold.dto.SeatMapEvent;
import com.ticketflow.seathold.dto.SeatStatus;
import com.ticketflow.seathold.service.SeatHoldRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Two responsibilities, both keyed off Redis:
 *
 * 1. {@link #onSeatMapMessage} — receives {@link SeatMapEvent}s published by
 *    ANY instance (including this one) on {@code seatmap:<showId>} and
 *    forwards them to locally-connected STOMP clients. This is what makes
 *    real-time updates work correctly when the app is horizontally scaled:
 *    a hold placed on instance A is seen by a browser connected to instance B.
 *
 * 2. {@link #onKeyExpired} — receives Redis keyspace notifications when a
 *    {@code seat:hold:*} key's TTL lapses (customer abandoned checkout).
 *    This is the auto-release mechanism: no polling/cron needed, Redis tells
 *    us the instant a hold expires, and we broadcast AVAILABLE for that seat.
 */
@Component
public class SeatMapRedisSubscriber {

    private static final Logger log = LoggerFactory.getLogger(SeatMapRedisSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final SeatHoldRedisService seatHoldRedisService;

    public SeatMapRedisSubscriber(
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper,
            SeatHoldRedisService seatHoldRedisService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.seatHoldRedisService = seatHoldRedisService;
    }

    public void onSeatMapMessage(String message) {
        try {
            SeatMapEvent event = objectMapper.readValue(message, SeatMapEvent.class);
            messagingTemplate.convertAndSend("/topic/shows/" + event.showId() + "/seatmap", event);
        } catch (Exception ex) {
            log.warn("Failed to process seat-map pub/sub message: {}", ex.getMessage());
        }
    }

    /**
     * Redis key format: {@code seat:hold:<showId>:<seatId>}. On expiry we
     * only get the key name (Redis doesn't retain the value), so we parse
     * showId/seatId out of it and broadcast that single seat as AVAILABLE.
     */
    public void onKeyExpired(String expiredKey) {
        if (!expiredKey.startsWith(SeatHoldRedisService.HOLD_KEY_PREFIX)) {
            return; // not a seat-hold key — ignore (other keyspaces may share this Redis instance)
        }

        try {
            String[] parts = expiredKey.substring(SeatHoldRedisService.HOLD_KEY_PREFIX.length()).split(":");
            UUID showId = UUID.fromString(parts[0]);
            UUID seatId = UUID.fromString(parts[1]);

            log.info("Seat hold expired (TTL auto-release): show={} seat={}", showId, seatId);

            seatHoldRedisService.cleanUpExpiredHoldMetadata(showId, seatId);

            SeatMapEvent event = SeatMapEvent.of(showId, List.of(seatId), SeatStatus.AVAILABLE, null);
            String payload = objectMapper.writeValueAsString(event);
            seatHoldRedisService.publishSeatMapEvent(showId, payload);
        } catch (Exception ex) {
            log.warn("Failed to handle expired key '{}': {}", expiredKey, ex.getMessage());
        }
    }
}
