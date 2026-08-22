package com.ticketflow.venueevent.repository;

import com.ticketflow.venueevent.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    Page<Event> findByOrganiserId(UUID organiserId, Pageable pageable);

    List<Event> findByOrganiserId(UUID organiserId);
}
