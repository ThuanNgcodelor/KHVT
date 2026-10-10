package com.example.quanlymuahang.web;

import com.example.quanlymuahang.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SpaController.class)
@Import(SecurityConfig.class)
class SpaWebIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserDetailsService users;

    @Test
    void anonymousNavigationAndDeepLinksReceiveTheFrontendEntryPoint() throws Exception {
        for (String path : new String[]{"/", "/login", "/modules", "/purchase-orders/123/edit", "/admin/users"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(header().string("Cache-Control", "no-cache"))
                    .andExpect(content().string(containsString("synthetic-spa-test-entry")));
        }
    }

    @Test
    void staticAssetsStaySeparateFromFrontendRoutes() throws Exception {
        mvc.perform(get("/assets/spa-test.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("synthetic-spa-test-asset")));
        mvc.perform(get("/assets/missing-test.js")).andExpect(status().isNotFound());
    }

    @Test
    void publicHtmlDoesNotGrantAccessToApiDataOrWrites() throws Exception {
        for (String path : new String[]{"/api/auth/me", "/api/admin/users", "/api/purchase-orders", "/api/unknown-test-route"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("code").value("UNAUTHENTICATED"));
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login")
                        .servletPath("/login").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder get(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).servletPath(path);
    }
}
