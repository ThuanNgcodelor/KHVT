package com.example.quanlymuahang.sharedkernel.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String MDC_KEY = "requestId";
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader("X-Request-ID");
        String requestId = supplied != null && supplied.matches("[A-Za-z0-9._-]{1,64}") ? supplied : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader("X-Request-ID", requestId);
        try { filterChain.doFilter(request, response); }
        finally { MDC.remove(MDC_KEY); }
    }
}
