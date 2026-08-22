package com.ticketflow.venueevent.service;

import com.ticketflow.venueevent.dto.EventRequest;
import com.ticketflow.venueevent.dto.EventResponse;
import com.ticketflow.venueevent.entity.Event;
import com.ticketflow.venueevent.exception.EventNotFoundException;
import com.ticketflow.venueevent.exception.NotEventOwnerException;
import com.ticketflow.venueevent.repository.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EventResponse createEvent(EventRequest request, UUID organiserId) {
        Event event = Event.builder()
                .organiserId(organiserId)
                .title(request.title())
                .type(request.type())
                .description(request.description())
                .language(request.language())
                .durationMinutes(request.durationMinutes())
                .posterUrl(request.posterUrl())
                .status("ACTIVE")
                .build();

        return toResponse(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(UUID eventId) {
        return toResponse(getEventOrThrow(eventId));
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> listEvents(Pageable pageable) {
        return eventRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> listMyEvents(UUID organiserId, Pageable pageable) {
        return eventRepository.findByOrganiserId(organiserId, pageable).map(this::toResponse);
    }

    @Transactional
    public EventResponse updateEvent(UUID eventId, EventRequest request, UUID organiserId) {
        Event event = getEventOrThrow(eventId);
        assertOwner(event, organiserId);

        event.setTitle(request.title());
        event.setType(request.type());
        event.setDescription(request.description());
        event.setLanguage(request.language());
        event.setDurationMinutes(request.durationMinutes());
        event.setPosterUrl(request.posterUrl());

        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public void delistEvent(UUID eventId, UUID organiserId) {
        Event event = getEventOrThrow(eventId);
        assertOwner(event, organiserId);
        event.setStatus("DELISTED");
        eventRepository.save(event);
    }

    Event getEventOrThrow(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
    }

    void assertOwner(Event event, UUID organiserId) {
        if (!event.getOrganiserId().equals(organiserId)) {
            throw new NotEventOwnerException();
        }
    }

    private EventResponse toResponse(Event e) {
        return new EventResponse(
                e.getId(), e.getOrganiserId(), e.getTitle(), e.getType(), e.getDescription(),
                e.getLanguage(), e.getDurationMinutes(), e.getPosterUrl(), e.getStatus(), e.getCreatedAt());
    }
}
