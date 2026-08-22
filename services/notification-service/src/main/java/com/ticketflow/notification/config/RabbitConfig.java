package com.ticketflow.notification.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pure consumer. Three queues, all declared in
 * {@code infra/rabbitmq/definitions.json} with dead-letter bindings:
 *
 * <ul>
 *   <li>{@code notification.booking-confirmed.q} → QR ticket email</li>
 *   <li>{@code notification.booking-cancelled.q} → cancellation confirmation</li>
 *   <li>{@code notification.waitlist-offer.q}    → time-limited claim link</li>
 * </ul>
 */
@Configuration
public class RabbitConfig {

    public static final String Q_BOOKING_CONFIRMED = "notification.booking-confirmed.q";
    public static final String Q_BOOKING_CANCELLED = "notification.booking-cancelled.q";
    public static final String Q_WAITLIST_OFFER = "notification.waitlist-offer.q";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
