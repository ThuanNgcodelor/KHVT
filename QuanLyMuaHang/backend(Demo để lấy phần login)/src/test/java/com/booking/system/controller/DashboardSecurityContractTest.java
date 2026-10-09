package com.booking.system.controller;

import com.booking.system.config.SecurityConfig;
import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import com.booking.system.repository.UserRepository;
import com.booking.system.security.JwtAuthFilter;
import com.booking.system.security.JwtUtils;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardSecurityContractTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private JwtUtils jwtUtils;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfig.class);
        context.refresh();

        jwtUtils = context.getBean(JwtUtils.class);
        userRepository = context.getBean(UserRepository.class);
        Filter securityFilter = context.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void dashboardApisReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/client")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/dashboard/admin")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeTokenCannotAccessApplicationApis() throws Exception {
        stubToken("employee-token", user("employee-id", RoleEnum.EMPLOYEE));

        mockMvc.perform(get("/api/v1/dashboard/client")
                        .header("Authorization", "Bearer employee-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/dashboard/admin")
                        .header("Authorization", "Bearer employee-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanReadAdminDashboard() throws Exception {
        stubToken("admin-token", user("admin-id", RoleEnum.ADMIN));

        mockMvc.perform(get("/api/v1/dashboard/admin")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    private void stubToken(String token, User user) {
        when(jwtUtils.validateJwtToken(token)).thenReturn(true);
        when(jwtUtils.getEmailFromJwtToken(token)).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private static User user(String id, RoleEnum role) {
        User user = new User();
        user.setId(id);
        user.setEmail(id + "@example.test");
        user.setFullName(role.name());
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestWebConfig {

        @Bean
        JwtUtils jwtUtils() {
            return mock(JwtUtils.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        JwtAuthFilter jwtAuthFilter(JwtUtils jwtUtils, UserRepository userRepository) {
            return new JwtAuthFilter(jwtUtils, userRepository);
        }

        @Bean
        DashboardSecurityController dashboardSecurityController() {
            return new DashboardSecurityController();
        }
    }

    @RestController
    static class DashboardSecurityController {

        @GetMapping("/api/v1/dashboard/client")
        String client() {
            return "ok";
        }

        @GetMapping("/api/v1/dashboard/admin")
        String admin() {
            return "ok";
        }
    }
}
