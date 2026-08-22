package com.ticketflow.waitlist.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * waitlist-service is both consumer and producer.
 *
 * <p>Consumes {@code waitlist.booking-cancelled.q} (bound to
 * {@code booking.events} / {@code booking.cancelled}) — a cancellation is the
 * trigger for auto-assignment.
 *
 * <p>Produces on {@code waitlist.events}:
 * <ul>
 *   <li>{@code waitlist.offer.created} → notification-service emails the
 *       time-limited claim link</li>
 *   <li>{@code waitlist.offer.accepted} → seat-hold-service turns the offer
 *       into a real TTL hold so checkout proceeds through the normal path</li>
 * </ul>
 */
@Configuration
public class RabbitConfig {

    public static final String WAITLIST_EXCHANGE = "waitlist.events";
    public static final String RK_OFFER_CREATED = "waitlist.offer.created";
    public static final String RK_OFFER_ACCEPTED = "waitlist.offer.accepted";

    public static final String Q_BOOKING_CANCELLED = "waitlist.booking-cancelled.q";

    @Bean
    public TopicExchange waitlistEventsExchange() {
        return new TopicExchange(WAITLIST_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate waitlistRabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }
}
