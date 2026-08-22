package com.ticketflow.venueevent.repository;

import com.ticketflow.venueevent.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
}
