package com.ticketflow.booking.messaging;

import com.ticketflow.booking.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes domain events to RabbitMQ AFTER the database transaction commits.
 *
 * <p>This ordering matters. If we published inside the transaction and the
 * transaction then rolled back (e.g. the unique-index race was lost), we'd
 * have emailed a QR ticket for a booking that does not exist. Spring's
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} gives us
 * "publish only if the booking is genuinely durable" for free.
 *
 * <p>The reverse failure — commit succeeds but RabbitMQ is down — leaves a
 * valid booking with no email. That's the acceptable direction of failure,
 * and it's recoverable: the booking is visible in history and the ticket can
 * be re-sent from the booking detail endpoint.
 */
@Component
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public BookingEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        send(RabbitConfig.RK_BOOKING_CONFIRMED, event, event.bookingReference());
    }

    @TransactionalEventListener
    public void onBookingCancelled(BookingCancelledEvent event) {
        send(RabbitConfig.RK_BOOKING_CANCELLED, event, event.bookingReference());
    }

    private void send(String routingKey, Object payload, String ref) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.BOOKING_EXCHANGE, routingKey, payload);
            log.info("Published {} for booking {}", routingKey, ref);
        } catch (Exception ex) {
            log.error("Failed to publish {} for booking {} — booking itself is committed and valid: {}",
                    routingKey, ref, ex.getMessage());
        }
    }
}
