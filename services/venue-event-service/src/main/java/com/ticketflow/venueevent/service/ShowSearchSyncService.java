package com.ticketflow.venueevent.service;

import com.ticketflow.venueevent.entity.Event;
import com.ticketflow.venueevent.entity.Show;
import com.ticketflow.venueevent.entity.ShowPricing;
import com.ticketflow.venueevent.entity.Venue;
import com.ticketflow.venueevent.repository.ShowPricingRepository;
import com.ticketflow.venueevent.search.ShowSearchDocument;
import com.ticketflow.venueevent.search.ShowSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class ShowSearchSyncService {

    private static final Logger log = LoggerFactory.getLogger(ShowSearchSyncService.class);

    private final ShowSearchRepository showSearchRepository;
    private final ShowPricingRepository showPricingRepository;

    public ShowSearchSyncService(ShowSearchRepository showSearchRepository, ShowPricingRepository showPricingRepository) {
        this.showSearchRepository = showSearchRepository;
        this.showPricingRepository = showPricingRepository;
    }

    /**
     * Upserts the ElasticSearch document for a show. Called after a show or
     * its pricing changes. Failures are logged, not thrown — ES indexing is
     * best-effort; Postgres is authoritative and a background reconciliation
     * job (or manual reindex endpoint) can repair drift.
     */
    public void indexShow(Show show, Event event, Venue venue) {
        try {
            List<ShowPricing> pricing = showPricingRepository.findByShowId(show.getId());

            BigDecimal min = pricing.stream().map(ShowPricing::getPrice).min(Comparator.naturalOrder()).orElse(null);
            BigDecimal max = pricing.stream().map(ShowPricing::getPrice).max(Comparator.naturalOrder()).orElse(null);

            ShowSearchDocument doc = ShowSearchDocument.builder()
                    .id(show.getId().toString())
                    .eventId(event.getId().toString())
                    .title(event.getTitle())
                    .eventType(event.getType().name())
                    .description(event.getDescription())
                    .language(event.getLanguage())
                    .venueId(venue.getId().toString())
                    .venueName(venue.getName())
                    .city(venue.getCity())
                    .showDateTime(show.getShowDateTime())
                    .status(show.getStatus().name())
                    .minPrice(min)
                    .maxPrice(max)
                    .build();

            showSearchRepository.save(doc);
        } catch (Exception ex) {
            log.warn("Failed to index show {} into ElasticSearch: {}", show.getId(), ex.getMessage());
        }
    }

    public void removeFromIndex(String showId) {
        try {
            showSearchRepository.deleteById(showId);
        } catch (Exception ex) {
            log.warn("Failed to remove show {} from ElasticSearch: {}", showId, ex.getMessage());
        }
    }
}
