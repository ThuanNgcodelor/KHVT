package com.example.quanlymuahang.identity;

import com.example.quanlymuahang.identity.infrastructure.persistence.RoleJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// H2 + servlet sessions: verifies real security filters/controllers, not Redis revocation.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "app.bootstrap-admin.email=", "app.bootstrap-admin.password=",
        "logging.level.org.springframework.web=INFO", "debug=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AuthApiIntegrationTest {
    private static final String EMAIL = "fixture@example.test";
    private static final String PASSWORD = "synthetic-test-password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired RoleJpaRepository roles;
    @Autowired UserAccountJpaRepository users;
    @Autowired PasswordEncoder encoder;
    @org.springframework.boot.test.mock.mockito.SpyBean com.example.quanlymuahang.identity.infrastructure.notification.EmailSenderService emailSender;

    @Test
    void anonymousAndMissingCsrfAreRejected() throws Exception {
        account("ADMIN", false, "*");
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", EMAIL, "password", "wrong"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("CSRF_INVALID"));
        assertThat(jdbc.queryForObject("select failed_login_count from user_accounts where email=?", Integer.class, EMAIL)).isZero();
    }

    @Test
    void invalidOrMismatchedCsrfDoesNotAttemptAuthentication() throws Exception {
        account("ADMIN", false, "*");
        Csrf original = csrf(null);
        Csrf replacement = csrf(null);
        String body = json.writeValueAsString(Map.of("email", EMAIL, "password", "wrong"));
        mvc.perform(post("/api/auth/login").cookie(original.cookie()).header(original.header(), "not-a-valid-csrf-token")
                .contentType("application/json").content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("CSRF_INVALID"));
        mvc.perform(post("/api/auth/login").cookie(replacement.cookie()).header(original.header(), original.token())
                .contentType("application/json").content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("CSRF_INVALID"));
        assertThat(jdbc.queryForObject("select failed_login_count from user_accounts where email=?", Integer.class, EMAIL)).isZero();
    }

    @Test
    void loginRestoresSessionAndLogoutInvalidatesIt() throws Exception {
        account("ADMIN", false, "*");
        MockHttpSession session = login();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("email").value(EMAIL))
                .andExpect(jsonPath("roles[0]").value("ADMIN"))
                .andExpect(jsonPath("permissions.length()").value(1)).andExpect(jsonPath("permissions[0]").value("*"))
                .andExpect(jsonPath("modules.length()").value(2))
                .andExpect(jsonPath("modules[0].code").value("PURCHASING"))
                .andExpect(jsonPath("modules[1].code").value("ADMINISTRATION"))
                .andExpect(jsonPath("password").doesNotExist()).andExpect(jsonPath("passwordHash").doesNotExist());
        Csrf csrf = csrf(session);
        mvc.perform(post("/api/auth/logout").session(session).cookie(csrf.cookie())
                .header(csrf.header(), csrf.token())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void temporaryPasswordBlocksDashboardUntilChanged() throws Exception {
        account("ADMIN", true, "*");
        MockHttpSession session = login();
        mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        Csrf csrf = csrf(session);
        changePassword(session, csrf, "short").andExpect(status().isBadRequest());
        changePassword(session, csrf, PASSWORD).andExpect(status().isBadRequest());
        changePassword(session, csrf, "new-synthetic-password").andExpect(status().isOk())
                .andExpect(jsonPath("mustChangePassword").value(false))
                .andExpect(jsonPath("permissions[0]").value("*"))
                .andExpect(jsonPath("modules.length()").value(2))
                .andExpect(jsonPath("passwordHash").doesNotExist());
        mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isOk());
    }

    @Test
    void viewerReadsDashboardButCannotAdministerUsersOrPersonnel() throws Exception {
        account("VIEWER", false, "PO_READ", "CATALOG_READ", "PRICE_READ");
        MockHttpSession session = login();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("permissions.length()").value(3))
                .andExpect(jsonPath("modules.length()").value(1))
                .andExpect(jsonPath("modules[0].code").value("PURCHASING"));
        mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("FORBIDDEN"));
        mvc.perform(get("/api/personnel/employees").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void hrReadsPersonnelButCannotReadPurchasingDashboard() throws Exception {
        account("HR_MANAGER", false, "PERSONNEL_READ", "PERSONNEL_MANAGE");
        MockHttpSession session = login();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("permissions.length()").value(2))
                .andExpect(jsonPath("modules.length()").value(1))
                .andExpect(jsonPath("modules[0].code").value("ADMINISTRATION"))
                .andExpect(jsonPath("modules[0].entryPath").value("/admin/employees"));
        mvc.perform(get("/api/personnel/employees").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void ungrantedAccountHasNoModulesAndCannotBypassPortalWithDirectApiCalls() throws Exception {
        account("NO_GRANTS", false);
        MockHttpSession session = login();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("permissions").isEmpty()).andExpect(jsonPath("modules").isEmpty());
        mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/personnel/employees").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void inactiveRoleDoesNotGrantModulesOrEffectivePermissions() throws Exception {
        account("INACTIVE_ADMIN", false, false, "*");
        MockHttpSession session = login();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("permissions").isEmpty()).andExpect(jsonPath("modules").isEmpty());
        mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/personnel/employees").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void customRoleGetsPurchasingFromPermissionsWithoutBuiltInRoleName() throws Exception {
        account("BUYING_READ_ONLY", false, "PO_READ", "CATALOG_READ");
        MockHttpSession session = login();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("roles[0]").value("BUYING_READ_ONLY"))
                .andExpect(jsonPath("modules.length()").value(1))
                .andExpect(jsonPath("modules[0].code").value("PURCHASING"))
                .andExpect(jsonPath("modules[0].entryPath").value("/dashboard"));
        mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void roleListingReportsModulesDerivedFromRolePermissions() throws Exception {
        account("ACCESS_ADMIN", false, "USER_READ");
        MockHttpSession session = login();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("modules.length()").value(1))
                .andExpect(jsonPath("modules[0].code").value("ADMINISTRATION"));
        mvc.perform(get("/api/admin/roles").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("length()").value(1))
                .andExpect(jsonPath("[0].moduleCodes[0]").value("ADMINISTRATION"))
                .andExpect(jsonPath("[0].permissions[0]").value("USER_READ"))
                .andExpect(jsonPath("[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("content[0].password").doesNotExist())
                .andExpect(jsonPath("content[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void invalidCredentialsReturnGenericError() throws Exception {
        account("ADMIN", false, "*");
        Csrf csrf = csrf(null);
        mvc.perform(post("/api/auth/login").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("email", EMAIL, "password", "wrong"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("INVALID_CREDENTIALS"));
        assertThat(users.findByEmail(EMAIL).orElseThrow().getFailedLoginCount()).isEqualTo(1);
    }

    @Test
    void loginWithRememberMeSetsCookieAndRestoresSession() throws Exception {
        account("ADMIN", false, "*");
        Csrf csrf = csrf(null);
        MvcResult loginResult = mvc.perform(post("/api/auth/login").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD, "rememberMe", true))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(loginResult.getResponse().getHeaders("Set-Cookie")).anyMatch(h -> h.contains("QMH_REMEMBER="));

        Cookie rememberCookie = loginResult.getResponse().getCookie("QMH_REMEMBER");
        assertThat(rememberCookie).isNotNull();

        // Perform unauthenticated call to /api/auth/me using only the remember-me cookie (no session)
        MvcResult restoredResult = mvc.perform(get("/api/auth/me").cookie(rememberCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("email").value(EMAIL))
                .andReturn();

        // Session was restored, and token was rotated
        assertThat(restoredResult.getResponse().getHeaders("Set-Cookie")).anyMatch(h -> h.contains("QMH_REMEMBER="));
    }

    @Test
    void logoutRevokesRememberMeToken() throws Exception {
        account("ADMIN", false, "*");
        Csrf csrf = csrf(null);
        MvcResult loginResult = mvc.perform(post("/api/auth/login").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD, "rememberMe", true))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rememberCookie = loginResult.getResponse().getCookie("QMH_REMEMBER");
        assertThat(rememberCookie).isNotNull();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        Csrf sessionCsrf = csrf(session);
        MvcResult logoutResult = mvc.perform(post("/api/auth/logout").session(session).cookie(sessionCsrf.cookie(), rememberCookie).header(sessionCsrf.header(), sessionCsrf.token()))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie clearedCookie = logoutResult.getResponse().getCookie("QMH_REMEMBER");
        assertThat(clearedCookie).isNotNull();
        assertThat(clearedCookie.getMaxAge()).isZero();

        // Calling /api/auth/me with old remember cookie should fail
        mvc.perform(get("/api/auth/me").cookie(rememberCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordSendsEmailAndAllowsResettingPassword() throws Exception {
        account("ADMIN", false, "*");
        Csrf csrf = csrf(null);

        // 1. Request forgot password
        mvc.perform(post("/api/auth/forgot-password").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("email", EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("message").exists());

        org.mockito.ArgumentCaptor<String> urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(emailSender).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.any(), urlCaptor.capture(), org.mockito.ArgumentMatchers.anyInt());

        String resetUrl = urlCaptor.getValue();
        assertThat(resetUrl).contains("token=");
        String rawToken = resetUrl.substring(resetUrl.indexOf("token=") + 6);

        // 2. Reset password using captured token
        String newPassword = "super-brand-new-secret-password-1234";
        mvc.perform(post("/api/auth/reset-password").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("token", rawToken, "newPassword", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("message").exists());

        // 3. Login with old password fails
        mvc.perform(post("/api/auth/login").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());

        // 4. Login with new password succeeds
        mvc.perform(post("/api/auth/login").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("email", EMAIL, "password", newPassword))))
                .andExpect(status().isOk());

        // 5. Reusing the same reset token fails
        mvc.perform(post("/api/auth/reset-password").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("token", rawToken, "newPassword", "another-new-password-1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_OR_EXPIRED_TOKEN"));
    }

    @Test
    void resetPasswordWithInvalidTokenFails() throws Exception {
        account("ADMIN", false, "*");
        Csrf csrf = csrf(null);

        mvc.perform(post("/api/auth/reset-password").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("token", "completely-bogus-token", "newPassword", "another-new-password-1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_OR_EXPIRED_TOKEN"));
    }

    @Test
    void resetPasswordReusingOldPasswordFails() throws Exception {
        account("ADMIN", false, "*");
        Csrf csrf = csrf(null);

        mvc.perform(post("/api/auth/forgot-password").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("email", EMAIL))))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<String> urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(emailSender).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.any(), urlCaptor.capture(), org.mockito.ArgumentMatchers.anyInt());

        String rawToken = urlCaptor.getValue().substring(urlCaptor.getValue().indexOf("token=") + 6);

        mvc.perform(post("/api/auth/reset-password").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("token", rawToken, "newPassword", PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("PASSWORD_REUSE"));
    }

    private void account(String role, boolean temporary, String... permissions) {
        account(role, temporary, true, permissions);
    }

    private void account(String role, boolean temporary, boolean activeRole, String... permissions) {
        jdbc.update("insert into roles(code,name,system_role,active,created_at,updated_at) values (?,?,true,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", role, role, activeRole);
        for (String permission : permissions) {
            jdbc.update("insert into permissions(code,name,system_permission) values (?,?,true)", permission, permission);
            jdbc.update("insert into role_permissions(role_id,permission_id) select r.id,p.id from roles r,permissions p where r.code=? and p.code=?", role, permission);
        }
        UserAccountEntity account = new UserAccountEntity(EMAIL, "Test user", encoder.encode(PASSWORD));
        account.changePassword(account.getPasswordHash(), temporary);
        account.replaceRoles(Set.of(roles.findByCode(role).orElseThrow()));
        users.saveAndFlush(account);
    }

    private MockHttpSession login() throws Exception {
        Csrf csrf = csrf(null);
        MvcResult result = mvc.perform(post("/api/auth/login").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("password").doesNotExist()).andExpect(jsonPath("passwordHash").doesNotExist()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return new Csrf(cookie, body.get("headerName").asText(), body.get("token").asText());
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(MockHttpSession session, Csrf csrf, String password) throws Exception {
        return mvc.perform(post("/api/auth/change-password").session(session).cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", password))));
    }
    private record Csrf(Cookie cookie, String header, String token) {}

    // Match the servlet path that Tomcat sets; MockMvc leaves it empty by default.
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder get(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).servletPath(path);
    }
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).servletPath(path);
    }
}
