package com.booking.system.controller;

import com.booking.system.dto.ApiResponse;
import com.booking.system.dto.NotificationResponse;
import com.booking.system.entity.User;
import com.booking.system.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getMyNotifications(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getNotificationsForUserPaged(requireUser(user).getId(), unreadOnly, pageable),
                "Lấy danh sách thông báo thành công"
        ));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotificationsCompat(
            @AuthenticationPrincipal User user,
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        User currentUser = requireUser(user);
        if (!currentUser.getId().equals(userId)) {
            return ResponseEntity.status(403).body(ApiResponse.error(403, "Không có quyền xem thông báo của người dùng khác"));
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getNotificationsForUserPaged(currentUser.getId(), unreadOnly, pageable),
                "Lấy danh sách thông báo thành công"
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(@AuthenticationPrincipal User user) {
        long count = notificationService.getUnreadCount(requireUser(user).getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count), "OK"));
    }

    @GetMapping("/unread-since")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnreadSince(
            @AuthenticationPrincipal User user,
            @RequestParam String since) {
        LocalDateTime sinceTime = LocalDateTime.parse(since);
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getUnreadSince(requireUser(user).getId(), sinceTime),
                "OK"
        ));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @AuthenticationPrincipal User user,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.markAsRead(id, requireUser(user).getId()),
                "Đánh dấu đã đọc thành công"
        ));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsReadCompat(
            @AuthenticationPrincipal User user,
            @PathVariable String id) {
        return markAsRead(user, id);
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(@AuthenticationPrincipal User user) {
        int updated = notificationService.markAllAsRead(requireUser(user).getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", updated), "Đánh dấu tất cả là đã đọc thành công"));
    }

    private User requireUser(User user) {
        if (user == null) {
            throw new RuntimeException("Chưa đăng nhập");
        }
        return user;
    }
}
