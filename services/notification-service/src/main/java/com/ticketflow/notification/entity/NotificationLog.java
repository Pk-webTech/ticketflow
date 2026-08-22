package com.ticketflow.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_log", schema = "notification")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false)
    private String recipient;

    private String subject;

    /** Natural key of the source event — the idempotency handle. */
    @Column(name = "dedupe_key", nullable = false, length = 120)
    private String dedupeKey;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
