package com.example.quanlymuahang.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises real Tomcat header handling with H2 and servlet sessions, not a live tunnel. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:production-proxy;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "app.bootstrap-admin.email=",
        "app.bootstrap-admin.password=",
        "logging.level.org.springframework.security=INFO"
})
@ActiveProfiles({"test", "prod"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductionProxyIntegrationTest {
    @LocalServerPort int port;
    @Autowired ServerProperties server;
    @Autowired ObjectMapper json;

    @Test
    void productionProfileRestrictsTrustedProxyAddresses() {
        assertThat(server.getAddress().isLoopbackAddress()).isTrue();
        assertThat(server.getForwardHeadersStrategy()).isEqualTo(ServerProperties.ForwardHeadersStrategy.NATIVE);
        assertThat(server.getTomcat().getRedirectContextRoot()).isFalse();
        assertThat(server.getServlet().getSession().getCookie().getSecure()).isTrue();
        assertThat(server.getTomcat().getRemoteip().getHostHeader()).isEmpty();
        assertThat(server.getTomcat().getRemoteip().getPortHeader()).isEmpty();
        Pattern trusted = Pattern.compile(server.getTomcat().getRemoteip().getInternalProxies());
        assertThat(trusted.matcher("127.0.0.1").matches()).isTrue();
        assertThat(trusted.matcher("::1").matches()).isTrue();
        assertThat(trusted.matcher("192.168.1.25").matches()).isFalse();
        assertThat(trusted.matcher("203.0.113.25").matches()).isFalse();
    }

    @Test
    void forwardedHttpsSetsSecureCsrfCookieThroughActualTomcat() throws Exception {
        HttpResponse<String> response = get("/api/auth/csrf", Map.of(
                "X-Forwarded-Proto", "https",
                "X-Forwarded-For", "203.0.113.25"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertCsrfCookie(response, true);
    }

    @Test
    void plainOrForwardedHttpDoesNotPretendToBeHttps() throws Exception {
        HttpResponse<String> plain = get("/api/auth/csrf", Map.of());
        assertThat(plain.statusCode()).isEqualTo(200);
        assertCsrfCookie(plain, false);

        HttpResponse<String> forwarded = get("/api/auth/csrf", Map.of("X-Forwarded-Proto", "http"));
        assertThat(forwarded.statusCode()).isEqualTo(200);
        assertCsrfCookie(forwarded, false);
    }

    @Test
    void standardForwardedHeaderCannotOverrideNativeProtocolHandling() throws Exception {
        HttpResponse<String> forged = get("/api/auth/csrf", Map.of(
                "Forwarded", "for=203.0.113.25;proto=https;host=attacker.invalid"));
        assertThat(forged.statusCode()).isEqualTo(200);
        assertCsrfCookie(forged, false);

        HttpResponse<String> conflicting = get("/api/auth/csrf", Map.of(
                "Forwarded", "for=203.0.113.25;proto=https;host=attacker.invalid",
                "X-Forwarded-Proto", "http"));
        assertThat(conflicting.statusCode()).isEqualTo(200);
        assertCsrfCookie(conflicting, false);
    }

    @Test
    void forgedHostAndPortHeadersDoNotChangeSameOriginIntoCrossOrigin() throws Exception {
        // MVC would reject this Origin if forwarded headers changed the request host or port.
        HttpResponse<String> response = get("/api/auth/csrf", Map.of(
                "Origin", "http://127.0.0.1:" + port,
                "X-Forwarded-Host", "attacker.invalid",
                "X-Forwarded-Port", "443",
                "Forwarded", "for=203.0.113.25;proto=https;host=attacker.invalid"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertCsrfCookie(response, false);
    }

    @Test
    void anonymousApiStillReturnsJsonAuthenticationErrorBehindProxy() throws Exception {
        HttpResponse<String> response = get("/api/auth/me", Map.of("X-Forwarded-Proto", "https"));
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
                .describedAs("anonymous API response has JSON content type").isTrue();
        assertThat("UNAUTHENTICATED".equals(json.readTree(response.body()).path("code").asText()))
                .describedAs("anonymous API response retains its authentication error code").isTrue();
    }

    private HttpResponse<String> get(String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET();
        headers.forEach(request::header);
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void assertCsrfCookie(HttpResponse<?> response, boolean expectedSecure) {
        // Assert booleans only: failures must not dump tokens or complete Set-Cookie headers.
        var cookies = response.headers().allValues("Set-Cookie");
        assertThat(cookies.stream().anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN=")))
                .describedAs("CSRF cookie was issued").isTrue();
        boolean secure = cookies.stream().filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                .anyMatch(cookie -> Arrays.stream(cookie.split(";"))
                        .anyMatch(attribute -> attribute.trim().equalsIgnoreCase("Secure")));
        assertThat(secure).describedAs("CSRF cookie Secure attribute matches the forwarded protocol")
                .isEqualTo(expectedSecure);
    }
}
