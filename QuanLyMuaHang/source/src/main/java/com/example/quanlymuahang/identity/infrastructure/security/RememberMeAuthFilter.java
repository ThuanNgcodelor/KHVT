package com.example.quanlymuahang.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public class RememberMeAuthFilter extends OncePerRequestFilter {
    private final RememberMeService rememberMeService;
    private final SecurityContextRepository securityContextRepository;

    public RememberMeAuthFilter(RememberMeService rememberMeService, SecurityContextRepository securityContextRepository) {
        this.rememberMeService = rememberMeService;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth == null || existingAuth instanceof AnonymousAuthenticationToken || !existingAuth.isAuthenticated()) {
            Optional<AccountPrincipal> principalOpt = rememberMeService.authenticate(request, response);
            if (principalOpt.isPresent()) {
                AccountPrincipal principal = principalOpt.get();
                Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                SecurityContextHolder.setContext(context);
                securityContextRepository.saveContext(context, request, response);
            }
        }
        filterChain.doFilter(request, response);
    }
}
