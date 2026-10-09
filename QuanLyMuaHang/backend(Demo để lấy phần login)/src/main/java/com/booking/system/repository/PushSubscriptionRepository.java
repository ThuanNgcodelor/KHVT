package com.booking.system.repository;

import com.booking.system.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, String> {
    List<PushSubscription> findByUserIdAndIsActiveTrue(String userId);

    List<PushSubscription> findByUserIdOrderByLastSeenAtDescCreatedAtDesc(String userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    Optional<PushSubscription> findByEndpointAndUserId(String endpoint, String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO push_subscriptions (
                id, user_id, endpoint, p256dh_key, auth_key, expiration_time,
                device_type, user_agent, is_active, created_at, last_seen_at, revoked_at
            ) VALUES (
                :id, :userId, :endpoint, :p256dhKey, :authKey, :expirationTime,
                :deviceType, :userAgent, true, :now, :now, null
            )
            ON DUPLICATE KEY UPDATE
                user_id = VALUES(user_id),
                p256dh_key = VALUES(p256dh_key),
                auth_key = VALUES(auth_key),
                expiration_time = VALUES(expiration_time),
                device_type = VALUES(device_type),
                user_agent = VALUES(user_agent),
                is_active = true,
                last_seen_at = VALUES(last_seen_at),
                revoked_at = null
            """, nativeQuery = true)
    int upsert(
            @Param("id") String id,
            @Param("userId") String userId,
            @Param("endpoint") String endpoint,
            @Param("p256dhKey") String p256dhKey,
            @Param("authKey") String authKey,
            @Param("expirationTime") LocalDateTime expirationTime,
            @Param("deviceType") String deviceType,
            @Param("userAgent") String userAgent,
            @Param("now") LocalDateTime now
    );
}
