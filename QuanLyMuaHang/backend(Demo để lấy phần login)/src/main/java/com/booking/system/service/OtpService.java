package com.booking.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final long OTP_TTL_MINUTES = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RedisTemplate<String, String> redisTemplate;

    // Sinh và lưu OTP 6 số vào Redis với TTL ngắn.
    public String generateAndStoreOtp(String key) {
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(key, otp, OTP_TTL_MINUTES, TimeUnit.MINUTES);
        return otp;
    }

    // Kiểm tra OTP, xoá ngay khi xác thực thành công để tránh dùng lại.
    public void verifyOtp(String key, String otp) {
        String savedOtp = redisTemplate.opsForValue().get(key);
        if (savedOtp == null || !savedOtp.equals(otp)) {
            throw new RuntimeException("OTP không đúng hoặc đã hết hạn");
        }
        redisTemplate.delete(key);
    }

    public void deleteOtp(String key) {
        redisTemplate.delete(key);
    }

    public String registerKey(String email) {
        return "otp:register:" + normalizeEmail(email);
    }

    public String forgotPasswordKey(String email) {
        return "otp:forgot-password:" + normalizeEmail(email);
    }

    public String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
