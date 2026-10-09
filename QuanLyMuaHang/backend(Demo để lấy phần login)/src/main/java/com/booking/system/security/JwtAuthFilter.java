package com.booking.system.security;

import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import com.booking.system.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = parseJwt(request);
            boolean protectedApi = request.getRequestURI().startsWith("/api/v1/hr/")
                    || request.getRequestURI().startsWith("/api/v1/notifications");
            if (jwt == null && protectedApi) {
                System.err.println("[JWT] Missing Authorization header for " + request.getRequestURI());
            }
            boolean validToken = jwt != null && jwtUtils.validateJwtToken(jwt);
            if (!validToken && jwt != null && protectedApi) {
                // JwtUtils logs the validation reason; this adds only the route,
                // never the token value.
                System.err.println("[JWT] Rejected token for " + request.getRequestURI());
            }
            if (validToken) {
                String email = jwtUtils.getEmailFromJwtToken(jwt);
                User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("User not found"));

                // A token may remain cryptographically valid after an account is
                // disabled. Never rebuild an authenticated principal for such an
                // account; protected endpoints must treat that request as anonymous.
                boolean loginRole = user.getRole() == RoleEnum.ADMIN || user.getRole() == RoleEnum.MANAGER;
                if (user.getStatus() == UserStatus.ACTIVE && loginRole) {
                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user, null, authorities);

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else if (protectedApi) {
                    System.err.println("[JWT] Authentication refused for inactive or disallowed user on "
                            + request.getRequestURI());
                }
            }
        } catch (Exception e) {
            System.err.println("Cannot set user authentication: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}
