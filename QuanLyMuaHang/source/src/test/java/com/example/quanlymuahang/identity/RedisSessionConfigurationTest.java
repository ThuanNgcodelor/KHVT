package com.example.quanlymuahang.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.session.RedisSessionProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSessionConfigurationTest {
    @Test
    void applicationConfigurationSelectsTheRepositoryRequiredForAccountRevocation() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));

        RedisSessionProperties properties = Binder.get(environment)
                .bind("spring.session.redis", Bindable.of(RedisSessionProperties.class))
                .orElseThrow(() -> new IllegalStateException("Missing Redis session configuration"));

        assertThat(properties.getRepositoryType()).isEqualTo(RedisSessionProperties.RepositoryType.INDEXED);
    }
}
