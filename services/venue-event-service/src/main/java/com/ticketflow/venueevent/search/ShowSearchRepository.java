package com.ticketflow.venueevent.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.time.Instant;

public interface ShowSearchRepository extends ElasticsearchRepository<ShowSearchDocument, String> {

    Page<ShowSearchDocument> findByTitleContainingIgnoreCaseAndStatus(String title, String status, Pageable pageable);

    Page<ShowSearchDocument> findByCityIgnoreCaseAndStatus(String city, String status, Pageable pageable);

    Page<ShowSearchDocument> findByEventTypeAndStatus(String eventType, String status, Pageable pageable);

    Page<ShowSearchDocument> findByCityIgnoreCaseAndEventTypeAndStatus(
            String city, String eventType, String status, Pageable pageable);

    Page<ShowSearchDocument> findByShowDateTimeBetweenAndStatus(Instant from, Instant to, String status, Pageable pageable);

    Page<ShowSearchDocument> findByStatus(String status, Pageable pageable);
}
