package com.booking.system.controller;

import com.booking.system.dto.ApiResponse;
import com.booking.system.dto.PushSubscriptionRequest;
import com.booking.system.dto.PushSubscriptionResponse;
import com.booking.system.entity.User;
import com.booking.system.service.PushService;
import com.booking.system.service.PushSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;
    private final PushService pushService;

    @GetMapping("/vapid-public-key")
    public ResponseEntity<ApiResponse<Map<String, String>>> getVapidPublicKey(@AuthenticationPrincipal User user) {
        requireUser(user);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("publicKey", pushService.getVapidPublicKey()),
                "Lấy VAPID public key thành công"
        ));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<ApiResponse<PushSubscriptionResponse>> subscribe(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PushSubscriptionRequest request) {
        PushSubscriptionResponse response = pushSubscriptionService.subscribe(requireUser(user).getId(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đăng ký nhận thông báo đẩy thành công"));
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String endpoint,
            @RequestBody(required = false) PushSubscriptionRequest request) {
        String resolvedEndpoint = endpoint != null ? endpoint : request == null ? null : request.getEndpoint();
        pushSubscriptionService.unsubscribe(requireUser(user).getId(), resolvedEndpoint);
        return ResponseEntity.ok(ApiResponse.success(null, "Hủy đăng ký thông báo đẩy thành công"));
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<List<PushSubscriptionResponse>>> getMySubscriptions(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(
                pushSubscriptionService.getSubscriptionsForUser(requireUser(user).getId()),
                "Lấy danh sách thiết bị nhận thông báo thành công"
        ));
    }

    private User requireUser(User user) {
        if (user == null) {
            throw new RuntimeException("Chưa đăng nhập");
        }
        return user;
    }
}
