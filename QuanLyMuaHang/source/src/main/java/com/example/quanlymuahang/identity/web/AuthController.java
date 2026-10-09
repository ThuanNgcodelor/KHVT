package com.example.quanlymuahang.identity.web;

import com.example.quanlymuahang.identity.application.AuthService;
import com.example.quanlymuahang.identity.infrastructure.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final AuthService authService;

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository, AuthService authService) {
        this.authenticationManager = authenticationManager; this.contextRepository = contextRepository; this.authService = authService;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) { return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken()); }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(body.email(), body.password()));
        } catch (AuthenticationException exception) {
            authService.recordLoginFailure(body.email());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthError("INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng"));
        }
        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        authService.recordLoginSuccess(principal.email());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return ResponseEntity.ok(MeResponse.from(principal));
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) { return MeResponse.from((AccountPrincipal) authentication.getPrincipal()); }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body, Authentication authentication) {
        authService.changePassword((AccountPrincipal) authentication.getPrincipal(), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 12, max = 200) String newPassword) {}
    public record CsrfResponse(String headerName, String token) {}
    public record AuthError(String code, String message) {}
    public record MeResponse(Long id, String email, String displayName, Long employeeId, Set<String> roles,
                             boolean mustChangePassword, Instant authenticatedAt) {
        static MeResponse from(AccountPrincipal principal) {
            return new MeResponse(principal.id(), principal.email(), principal.displayName(), principal.employeeId(),
                    principal.roles(), principal.mustChangePassword(), Instant.now());
        }
    }
}
