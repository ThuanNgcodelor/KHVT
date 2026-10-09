package com.booking.system.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "push_subscriptions",
        indexes = {
                @Index(name = "idx_push_subscriptions_user_active", columnList = "user_id,is_active"),
                @Index(name = "idx_push_subscriptions_last_seen", columnList = "last_seen_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_push_subscriptions_endpoint", columnNames = {"endpoint"})
        }
)
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "department"})
    private User user;

    @Column(nullable = false, length = 500)
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false, length = 255)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false, length = 255)
    private String authKey;

    @Column(name = "expiration_time")
    private LocalDateTime expirationTime;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
