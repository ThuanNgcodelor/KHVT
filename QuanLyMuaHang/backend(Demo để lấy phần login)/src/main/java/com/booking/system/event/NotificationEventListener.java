package com.booking.system.event;

import com.booking.system.dto.NotificationResponse;
import com.booking.system.entity.PushSubscription;
import com.booking.system.entity.User;
import com.booking.system.enums.NotificationType;
import com.booking.system.repository.UserRepository;
import com.booking.system.service.EmailService;
import com.booking.system.service.NotificationService;
import com.booking.system.service.PushService;
import com.booking.system.service.PushSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final PushService pushService;
    private final PushSubscriptionService pushSubscriptionService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationEvent event) {
        NotificationResponse payload = null;
        try {
            payload = notificationService.createNotification(
                    event.recipientId(),
                    event.senderId(),
                    event.type(),
                    event.title(),
                    event.message(),
                    event.targetUrl(),
                    event.sourceType(),
                    event.sourceId(),
                    event.priority()
            );
            if (payload != null) {
                notificationService.pushRealtime(event.recipientId(), payload);
            }
        } catch (Exception ex) {
            log.error("Failed to persist or push notification for recipient {} source {}:{}",
                    event.recipientId(), event.sourceType(), event.sourceId(), ex);
        }

        try {
            sendEmailIfConfigured(event);
        } catch (Exception ex) {
            log.error("Failed to trigger notification email for recipient {}", event.recipientId(), ex);
        }

        try {
            sendPushIfSubscribed(event, payload);
        } catch (Exception ex) {
            log.error("Failed to trigger Web Push for recipient {}", event.recipientId(), ex);
        }
    }

    private void sendEmailIfConfigured(NotificationEvent event) {
        if (event.emailInstruction() == null) {
            return;
        }

        User recipient = userRepository.findById(event.recipientId()).orElse(null);
        if (recipient == null) {
            return;
        }

        NotificationEvent.EmailInstruction email = event.emailInstruction();
        if (email.type() == NotificationEvent.EmailType.BOOKING_CREATED_TO_ADMIN) {
            emailService.sendBookingCreatedEmailToAdmin(
                    recipient.getEmail(),
                    email.requesterName(),
                    email.resourceType(),
                    email.title(),
                    email.bookingDetails()
            );
        } else if (email.type() == NotificationEvent.EmailType.BOOKING_APPROVED) {
            emailService.sendBookingApprovedEmail(
                    recipient.getEmail(), email.resourceType(), email.title(), email.bookingDetails());
        } else if (email.type() == NotificationEvent.EmailType.BOOKING_REJECTED) {
            emailService.sendBookingRejectedEmail(
                    recipient.getEmail(), email.resourceType(), email.title(), email.reason(), email.bookingDetails());
        } else if (email.type() == NotificationEvent.EmailType.PROFILE_UPDATE_REQUESTED_TO_ADMIN) {
            emailService.sendProfileUpdateRequestedEmailToAdmin(
                    recipient.getEmail(),
                    email.requesterName(),
                    email.title()
            );
        } else if (email.type() == NotificationEvent.EmailType.PROFILE_UPDATE_APPROVED) {
            emailService.sendProfileUpdateApprovedEmail(
                    recipient.getEmail(),
                    email.title()
            );
        } else if (email.type() == NotificationEvent.EmailType.PROFILE_UPDATE_REJECTED) {
            emailService.sendProfileUpdateRejectedEmail(
                    recipient.getEmail(),
                    email.title(),
                    email.reason()
            );
        } else if (email.type() == NotificationEvent.EmailType.ACCOUNT_REGISTRATION_PENDING) {
            emailService.sendAccountRegistrationPendingEmail(recipient.getEmail(), email.requesterName());
        } else if (email.type() == NotificationEvent.EmailType.ACCOUNT_REGISTRATION_APPROVED) {
            emailService.sendAccountRegistrationApprovedEmail(recipient.getEmail(), email.requesterName());
        } else if (email.type() == NotificationEvent.EmailType.ACCOUNT_REGISTRATION_REJECTED) {
            emailService.sendAccountRegistrationRejectedEmail(
                    recipient.getEmail(), email.requesterName(), email.reason());
        }
    }

    private void sendPushIfSubscribed(NotificationEvent event, NotificationResponse notification) {
        if (notification == null) {
            return;
        }

        Map<String, Object> pushPayload = new LinkedHashMap<>();
        long unreadCount = notificationService.getUnreadCount(event.recipientId());
        pushPayload.put("id", notification.getId());
        pushPayload.put("type", notification.getType());
        pushPayload.put("title", notification.getTitle());
        pushPayload.put("message", notification.getMessage());
        pushPayload.put("description", notification.getDescription());
        pushPayload.put("targetUrl", resolvePushTargetUrl(event, notification));
        pushPayload.put("sourceType", notification.getSourceType());
        pushPayload.put("sourceId", notification.getSourceId());
        pushPayload.put("priority", notification.getPriority());
        pushPayload.put("createdAt", notification.getCreatedAt() == null ? null : notification.getCreatedAt().toString());
        pushPayload.put("unreadCount", unreadCount);
        pushPayload.put("badgeCount", unreadCount);

        for (PushSubscription subscription : pushSubscriptionService.findActiveByUser(event.recipientId())) {
            pushService.sendPush(subscription, pushPayload);
        }
    }

    private String resolvePushTargetUrl(NotificationEvent event, NotificationResponse notification) {
        if (event.type() == NotificationType.BOOKING_PENDING_APPROVAL
                && event.sourceId() != null
                && ("BOOKING_ROOM".equals(event.sourceType()) || "BOOKING_CAR".equals(event.sourceType()))) {
            return "/admin/approvals/" + event.sourceId();
        }
        if (event.type() == NotificationType.PROFILE_UPDATE_REQUESTED && event.sourceId() != null) {
            return "/admin/profile-approvals/" + event.sourceId();
        }
        if (event.type() == NotificationType.PROFILE_UPDATE_APPROVED
                || event.type() == NotificationType.PROFILE_UPDATE_REJECTED) {
            return "/profile";
        }
        if (event.type() == NotificationType.ACCOUNT_REGISTRATION_REQUESTED) {
            return "/admin/users?tab=pending";
        }
        if (event.type() == NotificationType.ACCOUNT_REGISTRATION_APPROVED
                || event.type() == NotificationType.ACCOUNT_REGISTRATION_REJECTED
                || event.type() == NotificationType.ACCOUNT_REGISTRATION_PENDING) {
            return "/login";
        }
        return notification.getTargetUrl();
    }
}
