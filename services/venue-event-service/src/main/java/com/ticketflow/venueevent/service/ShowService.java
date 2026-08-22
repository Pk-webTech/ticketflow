package com.ticketflow.venueevent.service;

import com.ticketflow.venueevent.dto.ShowRequest;
import com.ticketflow.venueevent.dto.ShowResponse;
import com.ticketflow.venueevent.entity.*;
import com.ticketflow.venueevent.exception.InvalidSeatCategoryException;
import com.ticketflow.venueevent.exception.ShowNotFoundException;
import com.ticketflow.venueevent.exception.VenueNotFoundException;
import com.ticketflow.venueevent.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final ShowPricingRepository showPricingRepository;
    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final SeatCategoryRepository seatCategoryRepository;
    private final EventService eventService;
    private final ShowSearchSyncService searchSyncService;

    public ShowService(
            ShowRepository showRepository,
            ShowPricingRepository showPricingRepository,
            EventRepository eventRepository,
            VenueRepository venueRepository,
            SeatCategoryRepository seatCategoryRepository,
            EventService eventService,
            ShowSearchSyncService searchSyncService
    ) {
        this.showRepository = showRepository;
        this.showPricingRepository = showPricingRepository;
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
        this.seatCategoryRepository = seatCategoryRepository;
        this.eventService = eventService;
        this.searchSyncService = searchSyncService;
    }

    @Transactional
    public ShowResponse createShow(UUID eventId, ShowRequest request, UUID organiserId) {
        Event event = eventService.getEventOrThrow(eventId);
        eventService.assertOwner(event, organiserId);

        Venue venue = venueRepository.findById(request.venueId())
                .orElseThrow(() -> new VenueNotFoundException(request.venueId()));

        Map<UUID, SeatCategory> venueCategories = seatCategoryRepository
                .findByVenueIdOrderByDisplayOrderAsc(venue.getId()).stream()
                .collect(Collectors.toMap(SeatCategory::getId, c -> c));

        for (ShowRequest.CategoryPrice cp : request.pricing()) {
            if (!venueCategories.containsKey(cp.categoryId())) {
                throw new InvalidSeatCategoryException(cp.categoryId());
            }
        }

        Show show = Show.builder()
                .eventId(eventId)
                .venueId(venue.getId())
                .showDateTime(request.showDateTime())
                .status(ShowStatus.SCHEDULED)
                .build();
        show = showRepository.save(show);

        List<ShowPricing> pricingRows = request.pricing().stream()
                .map(cp -> ShowPricing.builder()
                        .showId(show.getId())
                        .categoryId(cp.categoryId())
                        .price(cp.price())
                        .build())
                .toList();
        showPricingRepository.saveAll(pricingRows);

        searchSyncService.indexShow(show, event, venue);

        return toResponse(show, venueCategories);
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(UUID showId) {
        Show show = getShowOrThrow(showId);
        Map<UUID, SeatCategory> categories = seatCategoryRepository
                .findByVenueIdOrderByDisplayOrderAsc(show.getVenueId()).stream()
                .collect(Collectors.toMap(SeatCategory::getId, c -> c));
        return toResponse(show, categories);
    }

    @Transactional(readOnly = true)
    public List<ShowResponse> listShowsForEvent(UUID eventId) {
        return showRepository.findByEventId(eventId).stream()
                .map(show -> {
                    Map<UUID, SeatCategory> categories = seatCategoryRepository
                            .findByVenueIdOrderByDisplayOrderAsc(show.getVenueId()).stream()
                            .collect(Collectors.toMap(SeatCategory::getId, c -> c));
                    return toResponse(show, categories);
                })
                .toList();
    }

    @Transactional
    public void cancelShow(UUID showId, UUID organiserId) {
        Show show = getShowOrThrow(showId);
        Event event = eventService.getEventOrThrow(show.getEventId());
        eventService.assertOwner(event, organiserId);

        show.setStatus(ShowStatus.CANCELLED);
        showRepository.save(show);

        Venue venue = venueRepository.findById(show.getVenueId())
                .orElseThrow(() -> new VenueNotFoundException(show.getVenueId()));
        searchSyncService.indexShow(show, event, venue); // re-index with CANCELLED status
    }

    private Show getShowOrThrow(UUID showId) {
        return showRepository.findById(showId).orElseThrow(() -> new ShowNotFoundException(showId));
    }

    private ShowResponse toResponse(Show show, Map<UUID, SeatCategory> categories) {
        List<ShowResponse.CategoryPriceView> pricing = showPricingRepository.findByShowId(show.getId()).stream()
                .map(p -> new ShowResponse.CategoryPriceView(
                        p.getCategoryId(),
                        categories.containsKey(p.getCategoryId()) ? categories.get(p.getCategoryId()).getName() : "Unknown",
                        p.getPrice()))
                .toList();

        return new ShowResponse(show.getId(), show.getEventId(), show.getVenueId(),
                show.getShowDateTime(), show.getStatus(), pricing);
    }
}
