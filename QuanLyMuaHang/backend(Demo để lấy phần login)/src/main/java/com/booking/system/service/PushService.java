package com.booking.system.service;

import com.booking.system.entity.PushSubscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Urgency;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.jar.JarException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushService {

    private final nl.martijndwars.webpush.PushService webPushClient;
    private final PushSubscriptionService pushSubscriptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.webpush.public-key:}")
    private String publicKey;

    @Value("${app.webpush.private-key:}")
    private String privateKey;

    @Value("${app.webpush.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.webpush.retry.backoff-ms:250}")
    private long retryBackoffMillis;

    public String getVapidPublicKey() {
        return publicKey == null ? "" : publicKey;
    }

    public void sendPush(PushSubscription subscription, Object payload) {
        if (!isConfigured()) {
            log.debug("Web Push is not configured; skipping subscription {}", subscription.getId());
            return;
        }

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.error("Failed to serialize Web Push payload for subscription {}", subscription.getId(), ex);
            return;
        }

        Notification notification;
        try {
            notification = Notification.builder()
                    .endpoint(subscription.getEndpoint())
                    .userPublicKey(subscription.getP256dhKey())
                    .userAuth(subscription.getAuthKey())
                    .payload(jsonPayload)
                    .ttl(24 * 60 * 60)
                    .urgency(Urgency.HIGH)
                    .build();
        } catch (Exception ex) {
            log.error("Failed to build Web Push notification for subscription {}", subscription.getId(), ex);
            return;
        }

        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                var response = webPushClient.send(notification, Encoding.AES128GCM);
                int statusCode = response.getStatusLine().getStatusCode();

                if (handleStatus(subscription, statusCode)) {
                    return;
                }

                if (isRetryableStatus(statusCode) && attempt < attempts) {
                    if (!backoffBeforeRetry(subscription, attempt, attempts, "status " + statusCode)) {
                        return;
                    }
                    continue;
                }

                log.warn("Push send returned status {} for subscription {} after {} attempt(s)",
                        statusCode, subscription.getId(), attempt);
                return;
            } catch (GeneralSecurityException ex) {
                log.error("Permanent crypto error sending Web Push to subscription {} - will not retry: {}",
                        subscription.getId(), ex.getMessage(), ex);
                return;
            } catch (SecurityException ex) {
                log.error("Permanent JCE provider error sending Web Push to subscription {} - will not retry: {}",
                        subscription.getId(), ex.getMessage(), ex);
                return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Web Push send interrupted for subscription {}", subscription.getId());
                return;
            } catch (IOException ex) {
                if (attempt < attempts) {
                    if (!backoffBeforeRetry(subscription, attempt, attempts, ex.getClass().getSimpleName())) {
                        return;
                    }
                    continue;
                }
                log.error("Network error sending Web Push to subscription {} after {} attempt(s)",
                        subscription.getId(), attempt, ex);
                return;
            } catch (Exception ex) {
                if (isNetworkFailure(ex)) {
                    if (attempt < attempts) {
                        if (!backoffBeforeRetry(subscription, attempt, attempts, "network failure")) {
                            return;
                        }
                        continue;
                    }
                    log.error("Network error sending Web Push to subscription {} after {} attempt(s)",
                            subscription.getId(), attempt, ex);
                    return;
                }

                log.error("Permanent Web Push failure for subscription {} - will not retry: {}",
                        subscription.getId(), ex.getMessage(), ex);
                return;
            }
        }
    }

    private boolean handleStatus(PushSubscription subscription, int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            pushSubscriptionService.markSendSuccess(subscription.getId());
            return true;
        }
        if (statusCode == 403 || statusCode == 404 || statusCode == 410) {
            pushSubscriptionService.deactivate(subscription.getId());
            log.info("Push subscription {} deactivated after status {}", subscription.getId(), statusCode);
            return true;
        }
        if (statusCode == 413) {
            log.warn("Push payload too large for subscription {}", subscription.getId());
            return true;
        }
        return false;
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private boolean isNetworkFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SecurityException
                    || current instanceof GeneralSecurityException
                    || current instanceof JarException) {
                return false;
            }
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean backoffBeforeRetry(
            PushSubscription subscription,
            int attempt,
            int attempts,
            String reason
    ) {
        log.warn("Retrying Web Push for subscription {} after {} (attempt {}/{})",
                subscription.getId(), reason, attempt + 1, attempts);

        if (retryBackoffMillis <= 0) {
            return true;
        }

        try {
            Thread.sleep(retryBackoffMillis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isConfigured() {
        return StringUtils.hasText(publicKey) && StringUtils.hasText(privateKey);
    }
}
