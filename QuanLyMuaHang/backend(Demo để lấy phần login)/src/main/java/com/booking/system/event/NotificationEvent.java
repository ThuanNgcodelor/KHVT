package com.booking.system.event;

import com.booking.system.enums.NotificationPriority;
import com.booking.system.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationEvent(
        String recipientId,
        String senderId,
        NotificationType type,
        String title,
        String message,
        String targetUrl,
        String sourceType,
        String sourceId,
        NotificationPriority priority,
        EmailInstruction emailInstruction
) {
    public NotificationEvent {
        if (priority == null) {
            priority = NotificationPriority.NORMAL;
        }
    }

    public record EmailInstruction(
            EmailType type,
            String resourceType,
            String requesterName,
            String title,
            String reason,
            BookingEmailDetails bookingDetails
    ) {
        public EmailInstruction(EmailType type, String resourceType, String requesterName, String title, String reason) {
            this(type, resourceType, requesterName, title, reason, null);
        }
    }

    public record BookingEmailDetails(
            String resourceName,
            String location,
            String departure,
            String destination,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {}

    public enum EmailType {
        BOOKING_CREATED_TO_ADMIN,
        BOOKING_APPROVED,
        BOOKING_REJECTED,
        PROFILE_UPDATE_REQUESTED_TO_ADMIN,
        PROFILE_UPDATE_APPROVED,
        PROFILE_UPDATE_REJECTED,
        ACCOUNT_REGISTRATION_PENDING,
        ACCOUNT_REGISTRATION_APPROVED,
        ACCOUNT_REGISTRATION_REJECTED
    }
}
