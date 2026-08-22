package com.ticketflow.venueevent.service;

import com.ticketflow.venueevent.search.ShowSearchDocument;
import com.ticketflow.venueevent.search.ShowSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SearchService {

    private static final String ACTIVE_STATUS = "SCHEDULED";

    private final ShowSearchRepository showSearchRepository;

    public SearchService(ShowSearchRepository showSearchRepository) {
        this.showSearchRepository = showSearchRepository;
    }

    public Page<ShowSearchDocument> search(String query, String city, String eventType,
                                            String fromDateIso, String toDateIso, Pageable pageable) {

        if (query != null && !query.isBlank()) {
            return showSearchRepository.findByTitleContainingIgnoreCaseAndStatus(query, ACTIVE_STATUS, pageable);
        }
        if (city != null && !city.isBlank() && eventType != null && !eventType.isBlank()) {
            return showSearchRepository.findByCityIgnoreCaseAndEventTypeAndStatus(city, eventType, ACTIVE_STATUS, pageable);
        }
        if (city != null && !city.isBlank()) {
            return showSearchRepository.findByCityIgnoreCaseAndStatus(city, ACTIVE_STATUS, pageable);
        }
        if (eventType != null && !eventType.isBlank()) {
            return showSearchRepository.findByEventTypeAndStatus(eventType, ACTIVE_STATUS, pageable);
        }
        if (fromDateIso != null && toDateIso != null) {
            Instant from = Instant.parse(fromDateIso);
            Instant to = Instant.parse(toDateIso);
            return showSearchRepository.findByShowDateTimeBetweenAndStatus(from, to, ACTIVE_STATUS, pageable);
        }

        return showSearchRepository.findByStatus(ACTIVE_STATUS, pageable);
    }
}
