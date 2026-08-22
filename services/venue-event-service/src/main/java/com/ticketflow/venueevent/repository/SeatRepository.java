package com.ticketflow.venueevent.repository;

import com.ticketflow.venueevent.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByVenueIdOrderByRowLabelAscSeatNumberAsc(UUID venueId);

    long countByVenueId(UUID venueId);

    void deleteByVenueId(UUID venueId);
}
