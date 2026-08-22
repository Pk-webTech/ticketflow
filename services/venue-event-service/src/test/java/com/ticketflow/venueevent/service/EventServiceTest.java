package com.ticketflow.venueevent.service;

import com.ticketflow.venueevent.dto.EventRequest;
import com.ticketflow.venueevent.entity.Event;
import com.ticketflow.venueevent.entity.EventType;
import com.ticketflow.venueevent.exception.EventNotFoundException;
import com.ticketflow.venueevent.exception.NotEventOwnerException;
import com.ticketflow.venueevent.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        eventService = new EventService(eventRepository);
    }

    @Test
    void createEventPersistsWithActiveStatus() {
        UUID organiserId = UUID.randomUUID();
        EventRequest request = new EventRequest("Inception", EventType.MOVIE, "A mind-bending thriller", "English", 148, null);

        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
            Event e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            e.setCreatedAt(Instant.now());
            e.setUpdatedAt(Instant.now());
            return e;
        });

        var response = eventService.createEvent(request, organiserId);

        assertThat(response.organiserId()).isEqualTo(organiserId);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.title()).isEqualTo("Inception");
    }

    @Test
    void updateEventRejectsNonOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID intruderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Event existing = Event.builder()
                .id(eventId).organiserId(ownerId).title("Concert Night")
                .type(EventType.CONCERT).status("ACTIVE")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(existing));

        EventRequest request = new EventRequest("Hacked Title", EventType.CONCERT, null, null, null, null);

        assertThatThrownBy(() -> eventService.updateEvent(eventId, request, intruderId))
                .isInstanceOf(NotEventOwnerException.class);
    }

    @Test
    void getEventThrowsWhenMissing() {
        UUID missingId = UUID.randomUUID();
        when(eventRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEvent(missingId))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void delistEventSucceedsForOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Event existing = Event.builder()
                .id(eventId).organiserId(ownerId).title("My Show")
                .type(EventType.MOVIE).status("ACTIVE")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(existing));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        eventService.delistEvent(eventId, ownerId);

        assertThat(existing.getStatus()).isEqualTo("DELISTED");
    }
}
