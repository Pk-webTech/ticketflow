package com.ticketflow.booking.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells seat-hold-service that a hold has become a permanent booking. That
 * service then drops the Redis TTL key (the seat is now BOOKED, not HELD —
 * it must not silently become available again when the TTL lapses) and marks
 * its audit row CONVERTED.
 *
 * <p>Deliberately best-effort: the booking row is already committed with the
 * DB unique index protecting it, so a transient failure here must not fail
 * the customer's checkout. Worst case the Redis key expires naturally a few
 * minutes later and the seat-map broadcast is corrected by the booked-seats
 * overlay.
 */
@Component
public class SeatHoldClient {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SeatHoldClient(RestTemplate restTemplate,
                          @Value("${services.seat-hold.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public void markConverted(UUID showId, UUID holdId, List<UUID> seatIds, UUID customerId, String bearerToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (bearerToken != null) {
                headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
            }
            Map<String, Object> payload = Map.of(
                    "showId", showId,
                    "holdId", holdId,
                    "seatIds", seatIds,
                    "customerId", customerId
            );
            restTemplate.postForEntity(
                    baseUrl + "/api/seats/internal/convert",
                    new HttpEntity<>(payload, headers),
                    Void.class
            );
        } catch (Exception ex) {
            log.warn("Could not notify seat-hold-service of conversion for hold {} (booking is still valid): {}",
                    holdId, ex.getMessage());
        }
    }
}
