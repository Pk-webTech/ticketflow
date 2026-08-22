package com.ticketflow.waitlist.messaging;

import com.ticketflow.waitlist.config.RabbitConfig;
import com.ticketflow.waitlist.service.WaitlistAssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The trigger for the entire waitlist flow: a customer cancelled, seats are
 * free, someone in the queue should get them.
 *
 * <p>Failures are allowed to propagate. The queue is configured with
 * {@code default-requeue-rejected: false} and a dead-letter binding, so a
 * poison message lands in the DLQ for inspection instead of blocking every
 * subsequent cancellation behind an infinite retry loop.
 */
@Component
public class BookingCancelledListener {

    private static final Logger log = LoggerFactory.getLogger(BookingCancelledListener.class);

    private final WaitlistAssignmentService assignmentService;

    public BookingCancelledListener(WaitlistAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @RabbitListener(queues = RabbitConfig.Q_BOOKING_CANCELLED)
    public void onBookingCancelled(BookingCancelledEvent event) {
        log.info("Cancellation received for booking {} — {} seat(s) freed on show {}",
                event.bookingReference(),
                event.freedSeats() == null ? 0 : event.freedSeats().size(),
                event.showId());

        assignmentService.assignFreedSeats(event);
    }
}
