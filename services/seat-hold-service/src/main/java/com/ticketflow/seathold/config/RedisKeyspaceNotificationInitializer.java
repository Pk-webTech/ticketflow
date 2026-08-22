package com.ticketflow.seathold.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisKeyspaceNotificationInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisKeyspaceNotificationInitializer.class);

    private final RedisConnectionFactory connectionFactory;

    public RedisKeyspaceNotificationInitializer(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (var connection = connectionFactory.getConnection()) {
            // "Ex" = keyspace events for expired keys — what drives our TTL auto-release notification.
            connection.serverCommands().setConfig("notify-keyspace-events", "Ex");
            log.info("Redis keyspace notifications enabled (notify-keyspace-events=Ex) for TTL auto-release detection");
        } catch (Exception ex) {
            log.warn("Could not enable Redis keyspace notifications automatically ({}). " +
                    "Auto-release broadcast on expiry may not fire — set 'notify-keyspace-events Ex' manually on the Redis server.",
                    ex.getMessage());
        }
    }
}
