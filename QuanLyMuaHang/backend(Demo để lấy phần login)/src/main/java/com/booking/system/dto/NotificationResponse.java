package com.booking.system.dto;

import com.booking.system.entity.Notification;
import com.booking.system.enums.NotificationPriority;
import com.booking.system.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponse {
    private String id;
    private NotificationType type;
    private String title;
    private String message;
    private String description;
    private String targetUrl;
    private String sourceType;
    private String sourceId;
    private NotificationPriority priority;
    private boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private Sender sender;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .description(notification.getMessage())
                .targetUrl(notification.getTargetUrl())
                .sourceType(notification.getSourceType())
                .sourceId(notification.getSourceId())
                .priority(notification.getPriority())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .sender(notification.getSender() == null ? null : Sender.builder()
                        .id(notification.getSender().getId())
                        .fullName(notification.getSender().getFullName())
                        .avatarUrl(notification.getSender().getAvatarUrl())
                        .build())
                .build();
    }

    @Getter
    @Builder
    public static class Sender {
        private String id;
        private String fullName;
        private String avatarUrl;
    }
}
