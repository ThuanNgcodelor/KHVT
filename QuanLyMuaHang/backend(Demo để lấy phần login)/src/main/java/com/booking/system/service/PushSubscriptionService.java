package com.booking.system.service;

import com.booking.system.dto.PushSubscriptionRequest;
import com.booking.system.dto.PushSubscriptionResponse;
import com.booking.system.entity.PushSubscription;
import com.booking.system.entity.User;
import com.booking.system.repository.PushSubscriptionRepository;
import com.booking.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserRepository userRepository;

    @Transactional
    public PushSubscriptionResponse subscribe(String userId, PushSubscriptionRequest request) {
        String p256dh = request.resolveP256dh();
        String auth = request.resolveAuth();
        if (!StringUtils.hasText(p256dh) || !StringUtils.hasText(auth)) {
            throw new RuntimeException("Push subscription thiếu khóa p256dh/auth");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        LocalDateTime now = LocalDateTime.now();
        pushSubscriptionRepository.upsert(
                UUID.randomUUID().toString(),
                user.getId(),
                request.getEndpoint(),
                p256dh,
                auth,
                toLocalDateTime(request.getExpirationTime()),
                request.getDeviceType(),
                limit(request.getUserAgent(), 500),
                now
        );

        PushSubscription subscription = pushSubscriptionRepository.findByEndpoint(request.getEndpoint())
                .orElseThrow(() -> new IllegalStateException("Push subscription was not persisted"));
        return PushSubscriptionResponse.from(subscription);
    }

    @Transactional
    public void unsubscribe(String userId, String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return;
        }
        pushSubscriptionRepository.findByEndpointAndUserId(endpoint, userId)
                .ifPresent(this::deactivate);
    }

    @Transactional
    public void deactivate(String subscriptionId) {
        pushSubscriptionRepository.findById(subscriptionId).ifPresent(this::deactivate);
    }

    @Transactional
    public void markSendSuccess(String subscriptionId) {
        pushSubscriptionRepository.findById(subscriptionId).ifPresent(subscription -> {
            subscription.setActive(true);
            subscription.setLastSeenAt(LocalDateTime.now());
            subscription.setRevokedAt(null);
        });
    }

    public List<PushSubscription> findActiveByUser(String userId) {
        return pushSubscriptionRepository.findByUserIdAndIsActiveTrue(userId);
    }

    public List<PushSubscriptionResponse> getSubscriptionsForUser(String userId) {
        return pushSubscriptionRepository.findByUserIdOrderByLastSeenAtDescCreatedAtDesc(userId)
                .stream()
                .map(PushSubscriptionResponse::from)
                .toList();
    }

    private void deactivate(PushSubscription subscription) {
        subscription.setActive(false);
        subscription.setRevokedAt(LocalDateTime.now());
        pushSubscriptionRepository.save(subscription);
    }

    private LocalDateTime toLocalDateTime(Long epochMillis) {
        if (epochMillis == null) {
            return null;
        }
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
