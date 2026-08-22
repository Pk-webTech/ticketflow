package com.ticketflow.venueevent.repository;

import com.ticketflow.venueevent.entity.SeatCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SeatCategoryRepository extends JpaRepository<SeatCategory, UUID> {

    List<SeatCategory> findByVenueIdOrderByDisplayOrderAsc(UUID venueId);

    void deleteByVenueId(UUID venueId);
}
