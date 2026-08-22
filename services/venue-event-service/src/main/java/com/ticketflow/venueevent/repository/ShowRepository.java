package com.ticketflow.venueevent.repository;

import com.ticketflow.venueevent.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShowRepository extends JpaRepository<Show, UUID> {

    List<Show> findByEventId(UUID eventId);

    List<Show> findByVenueId(UUID venueId);
}
