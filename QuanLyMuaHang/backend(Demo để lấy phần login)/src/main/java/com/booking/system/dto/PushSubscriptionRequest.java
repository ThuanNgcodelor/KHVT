package com.booking.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PushSubscriptionRequest {

    @NotBlank(message = "Push endpoint không được để trống")
    private String endpoint;

    private String p256dh;

    private String auth;

    private Long expirationTime;

    private String deviceType;

    private String userAgent;

    private Keys keys;

    public String resolveP256dh() {
        return p256dh != null ? p256dh : keys == null ? null : keys.getP256dh();
    }

    public String resolveAuth() {
        return auth != null ? auth : keys == null ? null : keys.getAuth();
    }

    @Data
    public static class Keys {
        private String p256dh;
        private String auth;
    }
}
