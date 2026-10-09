package com.booking.system.repository;

import com.booking.system.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    long countByRecipientIdAndIsReadFalse(String recipientId);

    Optional<Notification> findByIdAndRecipientId(String id, String recipientId);

    boolean existsByRecipientIdAndTypeAndSourceTypeAndSourceId(String recipientId, com.booking.system.enums.NotificationType type, String sourceType, String sourceId);

    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :recipientId AND n.isRead = false AND n.createdAt > :since ORDER BY n.createdAt DESC")
    List<Notification> findUnreadSince(@Param("recipientId") String recipientId, @Param("since") LocalDateTime since);

    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.recipient.id = :recipientId AND n.isRead = false")
    @org.springframework.data.jpa.repository.Modifying
    int markAllAsRead(@Param("recipientId") String recipientId, @Param("readAt") LocalDateTime readAt);
}
