package com.booking.system.hr.api;

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

class HrSecurityContractTest {

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
    void hrApiReturns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/hr/security-contract"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hrApiRejectsEmployeeToken() throws Exception {
        stubToken("employee-token", user("employee@example.test", RoleEnum.EMPLOYEE));

        mockMvc.perform(get("/api/v1/hr/security-contract")
                        .header("Authorization", "Bearer employee-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hrApiRejectsActiveUserWhenRoleIsUnset() throws Exception {
        User user = user("no-role@example.test", RoleEnum.EMPLOYEE);
        user.setRole(null);
        stubToken("no-role-token", user);

        mockMvc.perform(get("/api/v1/hr/security-contract")
                        .header("Authorization", "Bearer no-role-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hrApiAllowsAuthenticatedManagerAndAdmin() throws Exception {
        stubToken("manager-token", user("manager@example.test", RoleEnum.MANAGER));
        stubToken("admin-token", user("admin@example.test", RoleEnum.ADMIN));

        mockMvc.perform(get("/api/v1/hr/security-contract")
                        .header("Authorization", "Bearer manager-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/hr/security-contract")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void hrApiReturns401WhenManagerAccountIsNoLongerActive() throws Exception {
        User manager = user("inactive-manager@example.test", RoleEnum.MANAGER);
        manager.setStatus(UserStatus.INACTIVE);
        stubToken("inactive-manager-token", manager);

        mockMvc.perform(get("/api/v1/hr/security-contract")
                        .header("Authorization", "Bearer inactive-manager-token"))
                .andExpect(status().isUnauthorized());
    }

    private void stubToken(String token, User user) {
        when(jwtUtils.validateJwtToken(token)).thenReturn(true);
        when(jwtUtils.getEmailFromJwtToken(token)).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    void wordEditorManagementRequiresHrAndCallbackRequiresItsOwnToken() throws Exception {
        stubToken("editor-admin", user("editor-admin@example.test", RoleEnum.ADMIN));
        mockMvc.perform(get("/api/v1/hr/word-editor/sessions/s1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/hr/word-editor/sessions/s1").header("Authorization","Bearer editor-admin"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/word-editor/s1/content").param("ticket","bad"))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/word-editor/s1/callback")
                .contentType("application/json").content("{\"status\":2,\"key\":\"s1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeProfileEndpointsRequireActiveHrPrincipal() throws Exception {
        stubToken("profile-manager", user("profile-manager@example.test", RoleEnum.MANAGER));
        stubToken("profile-admin", user("profile-admin@example.test", RoleEnum.ADMIN));
        stubToken("profile-employee", user("profile-employee@example.test", RoleEnum.EMPLOYEE));
        for (String section : new String[]{"movements", "profile-audit", "contracts", "contracts/c1/documents"}) {
            String path = "/api/v1/hr/employees/e1/" + section;
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("Authorization", "Bearer profile-employee"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("Authorization", "Bearer profile-manager"))
                    .andExpect(status().isOk());
            mockMvc.perform(get(path).header("Authorization", "Bearer profile-admin"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void generalLaborCaptureApiAndImageRequireActiveHrAuthentication() throws Exception {
        stubToken("capture-manager", user("capture-manager@example.test", RoleEnum.MANAGER));
        stubToken("capture-employee", user("capture-employee@example.test", RoleEnum.EMPLOYEE));
        String root = "/api/v1/hr/general-labor/ocr-captures/8bc5e25a-aef7-43a1-b6fe-557843959ec4";
        mockMvc.perform(get(root)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(root + "/images/i1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(root).header("Authorization", "Bearer capture-employee")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(root).header("Authorization", "Bearer capture-manager"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store, private"));
    }

    private static User user(String email, RoleEnum role) {
        User user = new User();
        user.setId(role.name().toLowerCase() + "-id");
        user.setEmail(email);
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
        HrOcrCaptureController ocrCaptureController() {
            return new HrOcrCaptureController(mock(com.booking.system.hr.service.HrOcrCaptureService.class), new HrActorResolver());
        }

        @Bean
        HrWordEditorController wordEditorController() throws Exception {
            var service=mock(com.booking.system.hr.service.HrWordEditorService.class);
            org.mockito.Mockito.doThrow(new IllegalArgumentException("Invalid signature")).when(service).callback(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.isNull());
            when(service.source(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenThrow(new IllegalArgumentException("Invalid ticket"));
            return new HrWordEditorController(service,new HrActorResolver());
        }

        @Bean
        HrEmployeeProfileController employeeProfileController() {
            return new HrEmployeeProfileController(mock(HrEmployeeProfileQueryService.class));
        }

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
        SecurityContractController securityContractController() {
            return new SecurityContractController();
        }
    }

    @RestController
    static class SecurityContractController {

        @GetMapping("/api/v1/hr/security-contract")
        String ping() {
            return "ok";
        }
    }
}
