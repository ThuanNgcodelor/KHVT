package com.booking.system.service;

import com.booking.system.dto.NotificationResponse;
import com.booking.system.entity.Notification;
import com.booking.system.entity.User;
import com.booking.system.enums.NotificationPriority;
import com.booking.system.enums.NotificationType;
import com.booking.system.repository.NotificationRepository;
import com.booking.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationResponse createNotification(
            String recipientId,
            String senderId,
            NotificationType type,
            String title,
            String message,
            String targetUrl,
            String sourceType,
            String sourceId,
            NotificationPriority priority
    ) {
        if (sourceType != null && sourceId != null
                && notificationRepository.existsByRecipientIdAndTypeAndSourceTypeAndSourceId(recipientId, type, sourceType, sourceId)) {
            return null;
        }

        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người nhận thông báo"));
        User sender = senderId == null ? null : userRepository.findById(senderId).orElse(null);

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setSender(sender);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setTargetUrl(targetUrl);
        notification.setSourceType(sourceType);
        notification.setSourceId(sourceId);
        notification.setPriority(priority == null ? NotificationPriority.NORMAL : priority);

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    public Page<NotificationResponse> getNotificationsForUserPaged(String recipientId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId, pageable)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
        return page.map(NotificationResponse::from);
    }

    public List<NotificationResponse> getNotificationsForUser(String recipientId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationResponse markAsRead(String notificationId, String recipientId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông báo"));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
        }
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllAsRead(String recipientId) {
        return notificationRepository.markAllAsRead(recipientId, LocalDateTime.now());
    }

    public long getUnreadCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(recipientId);
    }

    public List<NotificationResponse> getUnreadSince(String recipientId, LocalDateTime since) {
        return notificationRepository.findUnreadSince(recipientId, since)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public void pushRealtime(String recipientId, NotificationResponse payload) {
        messagingTemplate.convertAndSendToUser(recipientId, "/queue/notifications", payload);
    }
}
