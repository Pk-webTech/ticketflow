package com.ticketflow.venueevent.service;

import com.ticketflow.venueevent.dto.*;
import com.ticketflow.venueevent.entity.Seat;
import com.ticketflow.venueevent.entity.SeatCategory;
import com.ticketflow.venueevent.entity.Venue;
import com.ticketflow.venueevent.exception.InvalidSeatCategoryException;
import com.ticketflow.venueevent.exception.VenueNotFoundException;
import com.ticketflow.venueevent.repository.SeatCategoryRepository;
import com.ticketflow.venueevent.repository.SeatRepository;
import com.ticketflow.venueevent.repository.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VenueService {

    private final VenueRepository venueRepository;
    private final SeatCategoryRepository seatCategoryRepository;
    private final SeatRepository seatRepository;

    public VenueService(
            VenueRepository venueRepository,
            SeatCategoryRepository seatCategoryRepository,
            SeatRepository seatRepository
    ) {
        this.venueRepository = venueRepository;
        this.seatCategoryRepository = seatCategoryRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public VenueResponse createVenue(VenueRequest request, UUID adminId) {
        Venue venue = Venue.builder()
                .name(request.name())
                .address(request.address())
                .city(request.city())
                .state(request.state())
                .postalCode(request.postalCode())
                .totalCapacity(0)
                .createdBy(adminId)
                .build();

        return toResponse(venueRepository.save(venue));
    }

    @Transactional(readOnly = true)
    public VenueResponse getVenue(UUID venueId) {
        return toResponse(getVenueOrThrow(venueId));
    }

    @Transactional(readOnly = true)
    public List<VenueResponse> listVenues() {
        return venueRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public VenueResponse updateVenue(UUID venueId, VenueRequest request) {
        Venue venue = getVenueOrThrow(venueId);
        venue.setName(request.name());
        venue.setAddress(request.address());
        venue.setCity(request.city());
        venue.setState(request.state());
        venue.setPostalCode(request.postalCode());
        return toResponse(venueRepository.save(venue));
    }

    @Transactional
    public void deleteVenue(UUID venueId) {
        getVenueOrThrow(venueId);
        seatRepository.deleteByVenueId(venueId);
        seatCategoryRepository.deleteByVenueId(venueId);
        venueRepository.deleteById(venueId);
    }

    // ---- Seat categories ------------------------------------------------

    @Transactional
    public SeatCategoryResponse addCategory(UUID venueId, SeatCategoryRequest request) {
        getVenueOrThrow(venueId);
        SeatCategory category = SeatCategory.builder()
                .venueId(venueId)
                .name(request.name())
                .displayColor(request.displayColor())
                .defaultPrice(request.defaultPrice())
                .displayOrder(request.displayOrder())
                .build();
        category = seatCategoryRepository.save(category);
        return toResponse(category);
    }

    @Transactional(readOnly = true)
    public List<SeatCategoryResponse> listCategories(UUID venueId) {
        return seatCategoryRepository.findByVenueIdOrderByDisplayOrderAsc(venueId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ---- Seat layout ------------------------------------------------------

    @Transactional
    public List<SeatResponse> defineSeatLayout(UUID venueId, SeatLayoutRequest request) {
        Venue venue = getVenueOrThrow(venueId);

        Map<UUID, SeatCategory> validCategories = seatCategoryRepository
                .findByVenueIdOrderByDisplayOrderAsc(venueId).stream()
                .collect(Collectors.toMap(SeatCategory::getId, c -> c));

        List<Seat> newSeats = new ArrayList<>();
        for (SeatLayoutRequest.RowBlock block : request.blocks()) {
            if (!validCategories.containsKey(block.categoryId())) {
                throw new InvalidSeatCategoryException(block.categoryId());
            }
            for (int seatNum = 1; seatNum <= block.seatCount(); seatNum++) {
                newSeats.add(Seat.builder()
                        .venueId(venueId)
                        .categoryId(block.categoryId())
                        .rowLabel(block.rowLabel())
                        .seatNumber(seatNum)
                        .section(block.section())
                        .build());
            }
        }

        List<Seat> saved = seatRepository.saveAll(newSeats);

        // Keep venue.totalCapacity in sync with the actual seat count.
        long total = seatRepository.countByVenueId(venueId);
        venue.setTotalCapacity((int) total);
        venueRepository.save(venue);

        return saved.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> getSeatLayout(UUID venueId) {
        return seatRepository.findByVenueIdOrderByRowLabelAscSeatNumberAsc(venueId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ---- helpers ------------------------------------------------------

    private Venue getVenueOrThrow(UUID venueId) {
        return venueRepository.findById(venueId).orElseThrow(() -> new VenueNotFoundException(venueId));
    }

    private VenueResponse toResponse(Venue v) {
        return new VenueResponse(
                v.getId(), v.getName(), v.getAddress(), v.getCity(), v.getState(),
                v.getPostalCode(), v.getTotalCapacity(), v.getCreatedBy(), v.getCreatedAt());
    }

    private SeatCategoryResponse toResponse(SeatCategory c) {
        return new SeatCategoryResponse(
                c.getId(), c.getVenueId(), c.getName(), c.getDisplayColor(), c.getDefaultPrice(), c.getDisplayOrder());
    }

    private SeatResponse toResponse(Seat s) {
        return new SeatResponse(s.getId(), s.getVenueId(), s.getCategoryId(), s.getRowLabel(), s.getSeatNumber(), s.getSection());
    }
}
