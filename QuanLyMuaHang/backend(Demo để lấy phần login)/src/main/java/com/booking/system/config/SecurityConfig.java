package com.booking.system.config;

import com.booking.system.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthFilter jwtAuthFilter;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .cors(Customizer.withDefaults())
                                .csrf(AbstractHttpConfigurer::disable)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                .requestMatchers(
                                                                "/favicon.ico",
                                                                "/logo*.png",
                                                                "/logotitle.png",
                                                                "/og-image*.png",
                                                                "/robots.txt",
                                                                "/sitemap.xml",
                                                                "/assets/**",
                                                                "/icons/**")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.HEAD,
                                                                "/",
                                                                "/index.html",
                                                                "/offline.html",
                                                                "/manifest.webmanifest",
                                                                "/login",
                                                                "/register",
                                                                "/forgot-password",
                                                                "/manager",
                                                                "/manager/**")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET,
                                                                "/",
                                                                "/index.html",
                                                                "/offline.html",
                                                                "/manifest.webmanifest",
                                                                "/sw.js",
                                                                "/login",
                                                                "/register",
                                                                "/forgot-password",
                                                                "/rooms/**",
                                                                "/cars/**",
                                                                "/notifications/**",
                                                                "/profile/**",
                                                                "/admin/**",
                                                                "/manager/**")
                                                .permitAll()
                                                .requestMatchers("/api/v1/auth/**").permitAll() // Mở endpoint đăng nhập
                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/v1/integrations/telegram/payroll/webhook")
                                                .permitAll()
                                                .requestMatchers("/api/v1/hr/sync/**").permitAll()
                                                .requestMatchers("/ws/**").permitAll()
                                                // CFCBase HR is an internal management system. Employees receive
                                                // payroll through Telegram and cannot access HR APIs.
                                                .requestMatchers(HttpMethod.GET, "/api/v1/word-editor/*/content").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/v1/word-editor/*/callback").permitAll()
                                                .requestMatchers("/api/v1/hr/**").hasAnyRole("ADMIN", "MANAGER")
                                                .requestMatchers("/api/v1/dashboard/admin").hasRole("ADMIN")
                                                .anyRequest().authenticated())
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint((request, response, exception) -> response
                                                                .sendError(HttpServletResponse.SC_UNAUTHORIZED))
                                                .accessDeniedHandler((request, response, exception) -> response
                                                                .sendError(HttpServletResponse.SC_FORBIDDEN)));

                http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }
}
