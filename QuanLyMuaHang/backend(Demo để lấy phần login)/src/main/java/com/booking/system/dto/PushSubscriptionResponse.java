package com.booking.system.dto;

import com.booking.system.entity.PushSubscription;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PushSubscriptionResponse {
    private String id;
    private String endpoint;
    private String deviceType;
    private String userAgent;
    private boolean isActive;
    private LocalDateTime expirationTime;
    private LocalDateTime createdAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime revokedAt;

    public static PushSubscriptionResponse from(PushSubscription subscription) {
        return PushSubscriptionResponse.builder()
                .id(subscription.getId())
                .endpoint(subscription.getEndpoint())
                .deviceType(subscription.getDeviceType())
                .userAgent(subscription.getUserAgent())
                .isActive(subscription.isActive())
                .expirationTime(subscription.getExpirationTime())
                .createdAt(subscription.getCreatedAt())
                .lastSeenAt(subscription.getLastSeenAt())
                .revokedAt(subscription.getRevokedAt())
                .build();
    }
}
