package com.ticketflow.seathold.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.seathold.messaging.SeatMapRedisSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    /** Channel pattern all instances subscribe to for seat-map fan-out. */
    public static final String SEATMAP_CHANNEL_PREFIX = "seatmap:";
    public static final String SEATMAP_CHANNEL_PATTERN = "seatmap:*";

    /** Redis keyspace-notification pattern for expired keys (requires notify-keyspace-events Ex on the server). */
    public static final String EXPIRED_KEYSPACE_PATTERN = "__keyevent@*__:expired";

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisHost, redisPort));
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            SeatMapRedisSubscriber seatMapRedisSubscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MessageListenerAdapter seatMapAdapter = new MessageListenerAdapter(seatMapRedisSubscriber, "onSeatMapMessage");
        container.addMessageListener(seatMapAdapter, new PatternTopic(SEATMAP_CHANNEL_PATTERN));

        MessageListenerAdapter expiryAdapter = new MessageListenerAdapter(seatMapRedisSubscriber, "onKeyExpired");
        container.addMessageListener(expiryAdapter, new PatternTopic(EXPIRED_KEYSPACE_PATTERN));

        return container;
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
}
