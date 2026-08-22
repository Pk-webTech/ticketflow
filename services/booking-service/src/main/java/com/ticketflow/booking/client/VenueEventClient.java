package com.ticketflow.booking.client;

import com.ticketflow.booking.exception.BookingExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Read-only client for venue-event-service.
 *
 * <p>Confirming a booking needs four things from it: the show (pricing +
 * venue/event ids), the venue seat layout (to resolve seat ids into labels and
 * categories), and the event and venue records (for the human-readable title
 * and venue name printed on the QR ticket).
 *
 * <p>The last two are fetched best-effort: if venue-event-service is briefly
 * unavailable we would rather issue a valid booking with a blank venue name
 * than fail a customer's paid checkout over cosmetic metadata. The show and
 * seat lookups are load-bearing and do throw.
 */
@Component
public class VenueEventClient {

    private static final Logger log = LoggerFactory.getLogger(VenueEventClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public VenueEventClient(RestTemplate restTemplate,
                            @Value("${services.venue-event.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public ShowDetailsDto getShow(UUID showId) {
        try {
            return restTemplate.getForObject(baseUrl + "/api/shows/" + showId, ShowDetailsDto.class);
        } catch (Exception ex) {
            throw new BookingExceptions.UpstreamServiceException("venue-event-service", ex.getMessage());
        }
    }

    public List<VenueSeatDto> getVenueSeats(UUID venueId) {
        try {
            return restTemplate.exchange(
                    baseUrl + "/api/venues/" + venueId + "/seats",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<VenueSeatDto>>() {
                    }
            ).getBody();
        } catch (Exception ex) {
            throw new BookingExceptions.UpstreamServiceException("venue-event-service", ex.getMessage());
        }
    }

    /** Best-effort — returns null rather than failing checkout. */
    public EventDetailsDto getEventOrNull(UUID eventId) {
        try {
            return restTemplate.getForObject(baseUrl + "/api/events/" + eventId, EventDetailsDto.class);
        } catch (Exception ex) {
            log.warn("Could not fetch event {} for booking metadata: {}", eventId, ex.getMessage());
            return null;
        }
    }

    /** Best-effort — returns null rather than failing checkout. */
    public VenueDetailsDto getVenueOrNull(UUID venueId) {
        try {
            return restTemplate.getForObject(baseUrl + "/api/venues/" + venueId, VenueDetailsDto.class);
        } catch (Exception ex) {
            log.warn("Could not fetch venue {} for booking metadata: {}", venueId, ex.getMessage());
            return null;
        }
    }
}
