package com.booking.system.entity;

import com.booking.system.enums.NotificationType;
import com.booking.system.enums.NotificationPriority;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notifications_recipient_read_created", columnList = "user_id,is_read,created_at"),
        @Index(name = "idx_notifications_recipient_created", columnList = "user_id,created_at"),
        @Index(name = "idx_notifications_source", columnList = "source_type,source_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_notifications_recipient_source_type", columnNames = {"user_id", "type", "source_type", "source_id"})
    }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "department"})
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "department"})
    private User sender;

    @Column(nullable = false)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String message;

    @Column(name = "target_url")
    private String targetUrl;

    @Column(name = "source_type", length = 64)
    private String sourceType;

    @Column(name = "source_id")
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private NotificationType type;

    @JsonProperty("isRead")
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public String getMessage() {
        return message != null ? message : description;
    }

    public void setMessage(String message) {
        this.message = message;
        this.description = message;
    }
}
