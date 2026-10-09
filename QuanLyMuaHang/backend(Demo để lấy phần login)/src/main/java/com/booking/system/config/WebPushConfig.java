package com.booking.system.config;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.security.GeneralSecurityException;
import java.security.Security;

@Slf4j
@Configuration
public class WebPushConfig {

    @Bean
    public nl.martijndwars.webpush.PushService webPushClient(
            @Value("${app.webpush.public-key:}") String publicKey,
            @Value("${app.webpush.private-key:}") String privateKey,
            @Value("${app.webpush.subject:https://cfcbooking.io.vn}") String subject)
            throws GeneralSecurityException {
        ensureBouncyCastleProvider();
        if (StringUtils.hasText(publicKey) && StringUtils.hasText(privateKey)) {
            return new nl.martijndwars.webpush.PushService(publicKey, privateKey, subject);
        }
        return new nl.martijndwars.webpush.PushService();
    }

    private void ensureBouncyCastleProvider() throws GeneralSecurityException {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null) {
            log.debug("Bouncy Castle JCE provider already registered");
            return;
        }

        int position = Security.addProvider(new BouncyCastleProvider());
        if (position < 1) {
            throw new GeneralSecurityException("Unable to register Bouncy Castle for Web Push");
        }
        log.info("Bouncy Castle JCE provider registered at position {}", position);
    }
}
