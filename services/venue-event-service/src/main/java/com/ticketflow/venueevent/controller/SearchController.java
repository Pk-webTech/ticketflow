package com.ticketflow.venueevent.controller;

import com.ticketflow.venueevent.search.ShowSearchDocument;
import com.ticketflow.venueevent.service.SearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Public browse/filter endpoint. All params optional — omit everything
     * to get all upcoming (SCHEDULED) shows.
     *   ?q=inception                 free-text title search
     *   ?city=Chennai                filter by venue city
     *   ?eventType=MOVIE             MOVIE | CONCERT
     *   ?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z   date range
     */
    @GetMapping
    public ResponseEntity<Page<ShowSearchDocument>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Pageable pageable
    ) {
        return ResponseEntity.ok(searchService.search(q, city, eventType, from, to, pageable));
    }
}
