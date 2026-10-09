package com.example.quanlymuahang.config;

import com.example.quanlymuahang.identity.infrastructure.security.AccountPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Restricts temporary-password accounts to the small set of endpoints needed to change credentials. */
final class PasswordChangeRequiredFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/auth/csrf", "/api/auth/login", "/api/auth/me",
            "/api/auth/change-password", "/api/auth/logout");
    private final ObjectMapper mapper;

    PasswordChangeRequiredFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AccountPrincipal principal
                && principal.mustChangePassword() && !ALLOWED_PATHS.contains(request.getServletPath())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), Map.of(
                    "code", "PASSWORD_CHANGE_REQUIRED",
                    "message", "Bạn cần đổi mật khẩu tạm trước khi sử dụng hệ thống"));
            return;
        }
        chain.doFilter(request, response);
    }
}
