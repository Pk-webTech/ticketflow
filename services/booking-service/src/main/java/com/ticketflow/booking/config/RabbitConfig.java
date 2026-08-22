package com.ticketflow.booking.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * booking-service is a pure PRODUCER on the {@code booking.events} topic
 * exchange. Two routing keys matter downstream:
 *
 * <ul>
 *   <li>{@code booking.confirmed} → notification-service (QR + email)</li>
 *   <li>{@code booking.cancelled} → notification-service (cancellation email)
 *       AND waitlist-service (triggers auto-assignment of the freed seats)</li>
 * </ul>
 *
 * The full exchange/queue/binding topology is declared in
 * {@code infra/rabbitmq/definitions.json} so it exists before any service
 * boots; the exchange is re-declared here only so local dev without that
 * definitions file still works.
 */
@Configuration
public class RabbitConfig {

    public static final String BOOKING_EXCHANGE = "booking.events";
    public static final String RK_BOOKING_CONFIRMED = "booking.confirmed";
    public static final String RK_BOOKING_CANCELLED = "booking.cancelled";

    @Bean
    public TopicExchange bookingEventsExchange() {
        return new TopicExchange(BOOKING_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate bookingRabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
