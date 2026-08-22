package com.ticketflow.waitlist.messaging;

import com.ticketflow.waitlist.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes after commit only — same reasoning as booking-service. An offer
 * email must never go out for an offer row that rolled back, or the customer
 * would click a claim link for an offer that does not exist.
 */
@Component
public class WaitlistEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WaitlistEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public WaitlistEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener
    public void onOfferCreated(WaitlistOfferCreatedEvent event) {
        send(RabbitConfig.RK_OFFER_CREATED, event);
    }

    @TransactionalEventListener
    public void onOfferAccepted(WaitlistOfferAcceptedEvent event) {
        send(RabbitConfig.RK_OFFER_ACCEPTED, event);
    }

    private void send(String routingKey, Object payload) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.WAITLIST_EXCHANGE, routingKey, payload);
            log.info("Published {}", routingKey);
        } catch (Exception ex) {
            log.error("Failed publishing {}: {}", routingKey, ex.getMessage());
        }
    }
}
