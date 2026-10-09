package com.example.quanlymuahang.identity;

import com.example.quanlymuahang.identity.infrastructure.security.SessionRevocationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt in with QMH_RUN_REDIS_TESTS=true and QMH_TEST_REDIS_HOST/PORT/PASSWORD.
 * Uses a UUID namespace and synthetic sessions, never application users or flushdb.
 */
@EnabledIfEnvironmentVariable(named = "QMH_RUN_REDIS_TESTS", matches = "(?i)true")
class RedisSessionRevocationIntegrationTest {
    @Test
    void bootIndexedRepositoryRevokesAccountSessionsAndPreservesOtherAccounts() {
        String namespace = "qmh:test:session:" + UUID.randomUUID();
        String principal = "employee-" + UUID.randomUUID() + "@example.test";
        String otherPrincipal = "other-" + UUID.randomUUID() + "@example.test";

        new WebApplicationContextRunner()
                .withInitializer(context -> loadApplicationYaml(context.getEnvironment().getPropertySources()))
                .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class, SessionAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "spring.data.redis.host=" + environment("QMH_TEST_REDIS_HOST", "127.0.0.1"),
                        "spring.data.redis.port=" + environment("QMH_TEST_REDIS_PORT", "6380"),
                        "spring.data.redis.password=" + environment("QMH_TEST_REDIS_PASSWORD", ""),
                        "spring.session.redis.namespace=" + namespace,
                        // Avoid changing shared Redis server settings just to run this test.
                        "spring.session.redis.configure-action=none")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedisIndexedSessionRepository.class);
                    assertThat(context.getBean(FindByIndexNameSessionRepository.class))
                            .isSameAs(context.getBean(RedisIndexedSessionRepository.class));

                    RedisIndexedSessionRepository repository = context.getBean(RedisIndexedSessionRepository.class);
                    SessionRevocationService revocation = context.getBean(SessionRevocationService.class);
                    StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
                    List<String> createdSessions = new ArrayList<>();
                    try {
                        String sessionA = save(repository, principal, createdSessions);
                        String sessionB = save(repository, principal, createdSessions);
                        String otherSession = save(repository, otherPrincipal, createdSessions);
                        assertThat(repository.findByPrincipalName(principal)).containsOnlyKeys(sessionA, sessionB);

                        revocation.revokeExcept(principal, sessionA);

                        assertThat(repository.findById(sessionA)).isNotNull();
                        assertThat(repository.findById(sessionB)).isNull();
                        assertThat(repository.findByPrincipalName(principal)).containsOnlyKeys(sessionA);
                        assertThat(repository.findById(otherSession)).isNotNull();

                        String nextSession = save(repository, principal, createdSessions);
                        revocation.revokeAll(principal);

                        assertThat(repository.findById(sessionA)).isNull();
                        assertThat(repository.findById(nextSession)).isNull();
                        assertThat(repository.findByPrincipalName(principal)).isEmpty();
                        assertThat(repository.findByPrincipalName(otherPrincipal)).containsOnlyKeys(otherSession);
                    } finally {
                        createdSessions.forEach(repository::deleteById);
                        deleteOwnNamespace(redis, namespace);
                    }
                });
    }

    private static String save(RedisIndexedSessionRepository repository, String principal, List<String> createdSessions) {
        var session = repository.createSession();
        createdSessions.add(session.getId());
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal);
        repository.save(session);
        return session.getId();
    }

    private static void deleteOwnNamespace(StringRedisTemplate redis, String namespace) {
        assertThat(namespace).startsWith("qmh:test:session:");
        UUID.fromString(namespace.substring("qmh:test:session:".length()));
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(namespace + ":*").count(100).build())) {
            cursor.forEachRemaining(key -> {
                assertThat(key).startsWith(namespace + ":");
                keys.add(key);
            });
        }
        if (!keys.isEmpty()) redis.delete(keys);
    }

    private static void loadApplicationYaml(org.springframework.core.env.MutablePropertySources sources) {
        try {
            new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                    .forEach(sources::addLast);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load application.yml for session configuration test", exception);
        }
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    @Configuration(proxyBeanMethods = false)
    @Import(SessionRevocationService.class)
    static class TestConfiguration {}
}
