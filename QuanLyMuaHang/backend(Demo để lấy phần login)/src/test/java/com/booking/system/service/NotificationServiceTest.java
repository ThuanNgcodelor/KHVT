package com.booking.system.service;

import com.booking.system.dto.NotificationResponse;
import com.booking.system.entity.Notification;
import com.booking.system.entity.User;
import com.booking.system.enums.NotificationPriority;
import com.booking.system.enums.NotificationType;
import com.booking.system.repository.NotificationRepository;
import com.booking.system.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createNotificationPersistsCorrectRecipient() {
        User recipient = user("user-1", "user@example.com");
        User sender = user("admin-1", "admin@example.com");
        when(notificationRepository.existsByRecipientIdAndTypeAndSourceTypeAndSourceId(
                "user-1", NotificationType.BOOKING_APPROVED, "BOOKING_ROOM", "booking-1")).thenReturn(false);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(recipient));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(sender));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId("notif-1");
            return notification;
        });

        NotificationResponse response = notificationService.createNotification(
                "user-1",
                "admin-1",
                NotificationType.BOOKING_APPROVED,
                "Đã duyệt",
                "Booking đã được duyệt",
                "/rooms",
                "BOOKING_ROOM",
                "booking-1",
                NotificationPriority.NORMAL
        );

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipient().getId()).isEqualTo("user-1");
        assertThat(captor.getValue().getSender().getId()).isEqualTo("admin-1");
        assertThat(response.getId()).isEqualTo("notif-1");
        assertThat(response.getTargetUrl()).isEqualTo("/rooms");
    }

    @Test
    void createNotificationReturnsNullForDuplicateSourceEvent() {
        when(notificationRepository.existsByRecipientIdAndTypeAndSourceTypeAndSourceId(
                "user-1", NotificationType.BOOKING_APPROVED, "BOOKING_ROOM", "booking-1")).thenReturn(true);

        NotificationResponse response = notificationService.createNotification(
                "user-1", null, NotificationType.BOOKING_APPROVED, "Đã duyệt", "OK",
                "/rooms", "BOOKING_ROOM", "booking-1", NotificationPriority.NORMAL);

        assertThat(response).isNull();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsReadRequiresOwner() {
        when(notificationRepository.findByIdAndRecipientId("notif-1", "user-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead("notif-1", "user-a"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không tìm thấy thông báo");
    }

    @Test
    void markAsReadSetsReadTimestampForOwner() {
        Notification notification = new Notification();
        notification.setId("notif-1");
        notification.setRecipient(user("user-a", "a@example.com"));
        notification.setType(NotificationType.SYSTEM);
        notification.setTitle("Thông báo");
        notification.setMessage("Nội dung");
        when(notificationRepository.findByIdAndRecipientId("notif-1", "user-a")).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = notificationService.markAsRead("notif-1", "user-a");

        assertThat(response.isRead()).isTrue();
        assertThat(response.getReadAt()).isNotNull();
    }

    @Test
    void unreadCountUsesRecipientScope() {
        when(notificationRepository.countByRecipientIdAndIsReadFalse("user-a")).thenReturn(3L);

        assertThat(notificationService.getUnreadCount("user-a")).isEqualTo(3L);
    }

    @Test
    void pushRealtimeUsesUserDestination() {
        NotificationResponse payload = NotificationResponse.builder()
                .id("notif-1")
                .type(NotificationType.SYSTEM)
                .title("Thông báo")
                .message("Nội dung")
                .priority(NotificationPriority.NORMAL)
                .build();

        notificationService.pushRealtime("user-a", payload);

        verify(messagingTemplate).convertAndSendToUser("user-a", "/queue/notifications", payload);
    }

    private User user(String id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(email);
        return user;
    }
}
