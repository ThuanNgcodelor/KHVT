package com.example.quanlymuahang.identity.infrastructure.security;

import com.example.quanlymuahang.identity.infrastructure.persistence.AccountStatus;
import com.example.quanlymuahang.identity.infrastructure.persistence.RememberMeTokenEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.RememberMeTokenJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeJpaRepository;
import com.example.quanlymuahang.personnel.infrastructure.persistence.EmployeeStatus;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RememberMeService {
    private static final Logger log = LoggerFactory.getLogger(RememberMeService.class);
    public static final String COOKIE_NAME = "QMH_REMEMBER";
    public static final int TOKEN_VALIDITY_DAYS = 30;

    private final RememberMeTokenJpaRepository tokens;
    private final UserAccountJpaRepository accounts;
    private final EmployeeJpaRepository employees;
    private final boolean sessionCookieSecure;

    public RememberMeService(RememberMeTokenJpaRepository tokens,
                             UserAccountJpaRepository accounts,
                             EmployeeJpaRepository employees,
                             @Value("${server.servlet.session.cookie.secure:false}") boolean sessionCookieSecure) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.employees = employees;
        this.sessionCookieSecure = sessionCookieSecure;
    }

    @Transactional
    public void createToken(Long userId, HttpServletRequest request, HttpServletResponse response) {
        if (userId == null) return;
        String series = UUID.randomUUID().toString().replace("-", "");
        String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String userAgent = truncate(request.getHeader(HttpHeaders.USER_AGENT), 255);
        String ipAddress = truncate(request.getRemoteAddr(), 45);
        Instant expiresAt = Instant.now().plus(TOKEN_VALIDITY_DAYS, ChronoUnit.DAYS);

        RememberMeTokenEntity entity = new RememberMeTokenEntity(userId, series, hash(rawToken), userAgent, ipAddress, expiresAt);
        tokens.save(entity);
        setCookie(response, series, rawToken);
    }

    @Transactional
    public Optional<AccountPrincipal> authenticate(HttpServletRequest request, HttpServletResponse response) {
        String cookieValue = extractCookieValue(request);
        if (cookieValue == null || !cookieValue.contains(":")) return Optional.empty();

        int colon = cookieValue.indexOf(':');
        String series = cookieValue.substring(0, colon);
        String rawToken = cookieValue.substring(colon + 1);
        if (series.isBlank() || rawToken.isBlank()) {
            clearCookie(response);
            return Optional.empty();
        }

        Optional<RememberMeTokenEntity> tokenOpt = tokens.findBySeries(series);
        if (tokenOpt.isEmpty()) {
            clearCookie(response);
            return Optional.empty();
        }

        RememberMeTokenEntity entity = tokenOpt.get();
        if (entity.getExpiresAt().isBefore(Instant.now())) {
            tokens.deleteBySeries(series);
            clearCookie(response);
            return Optional.empty();
        }

        String rawHash = hash(rawToken);
        boolean isCurrentMatch = rawHash.equals(entity.getTokenHash());
        boolean isRecentPreviousMatch = entity.getPreviousTokenHash() != null
                && rawHash.equals(entity.getPreviousTokenHash())
                && entity.getLastRotatedAt() != null
                && entity.getLastRotatedAt().isAfter(Instant.now().minus(Duration.ofSeconds(120)));

        if (!isCurrentMatch && !isRecentPreviousMatch) {
            log.warn("Remember-me token hash mismatch for series {}. Invalidate all tokens for user {}.", series, entity.getUserId());
            tokens.deleteAllByUserId(entity.getUserId());
            clearCookie(response);
            return Optional.empty();
        }

        Optional<UserAccountEntity> accountOpt = accounts.findById(entity.getUserId());
        if (accountOpt.isEmpty()) {
            tokens.deleteBySeries(series);
            clearCookie(response);
            return Optional.empty();
        }

        UserAccountEntity account = accountOpt.get();
        if (account.getStatus() != AccountStatus.ACTIVE || account.isLockedNow()) {
            tokens.deleteBySeries(series);
            clearCookie(response);
            return Optional.empty();
        }

        boolean linkedEmployeeActive = account.getEmployeeId() == null || employees.findById(account.getEmployeeId())
                .map(employee -> employee.getStatus() == EmployeeStatus.ACTIVE).orElse(false);
        if (!linkedEmployeeActive) {
            tokens.deleteBySeries(series);
            clearCookie(response);
            return Optional.empty();
        }

        // Token rotation: only rotate if matching current token AND not rotated within the last 60 seconds
        if (isCurrentMatch && (entity.getLastRotatedAt() == null || entity.getLastRotatedAt().isBefore(Instant.now().minus(Duration.ofSeconds(60))))) {
            String newRawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            entity.setPreviousTokenHash(entity.getTokenHash());
            entity.setTokenHash(hash(newRawToken));
            entity.setLastRotatedAt(Instant.now());
            entity.setExpiresAt(Instant.now().plus(TOKEN_VALIDITY_DAYS, ChronoUnit.DAYS));
            tokens.save(entity);
            setCookie(response, series, newRawToken);
        }

        return Optional.of(new AccountPrincipal(account, linkedEmployeeActive));
    }

    @Transactional
    public void revokeCurrentToken(HttpServletRequest request, HttpServletResponse response) {
        String cookieValue = extractCookieValue(request);
        if (cookieValue != null && cookieValue.contains(":")) {
            String series = cookieValue.substring(0, cookieValue.indexOf(':'));
            if (!series.isBlank()) tokens.deleteBySeries(series);
        }
        clearCookie(response);
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        if (userId != null) tokens.deleteAllByUserId(userId);
    }

    private void setCookie(HttpServletResponse response, String series, String token) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, series + ":" + token)
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofDays(TOKEN_VALIDITY_DAYS))
                .secure(sessionCookieSecure)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .secure(sessionCookieSecure)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractCookieValue(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
