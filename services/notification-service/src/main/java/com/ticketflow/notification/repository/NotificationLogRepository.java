package com.ticketflow.notification.repository;

import com.ticketflow.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    boolean existsByTypeAndDedupeKeyAndStatus(String type, String dedupeKey, String status);
}
