package com.booking.system.config;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.HttpEce;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyAgreement;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushCryptoCompatibilityTest {

    private static final String PREFERRED_PROVIDER_PROPERTY = "jdk.security.provider.preferred";
    private String originalPreferredProviders;

    @BeforeEach
    void forceGenericEcdhToSunEc() {
        originalPreferredProviders = Security.getProperty(PREFERRED_PROVIDER_PROPERTY);
        Security.setProperty(PREFERRED_PROVIDER_PROPERTY, "KeyAgreement.ECDH:SunEC");
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(new BouncyCastleProvider());
    }

    @AfterEach
    void restorePreferredProviders() {
        Security.setProperty(PREFERRED_PROVIDER_PROPERTY,
                originalPreferredProviders == null ? "" : originalPreferredProviders);
    }

    @Test
    void encryptsWithBouncyCastleWhenGenericEcdhResolvesToSunEc() throws Exception {
        assertThat(KeyAgreement.getInstance("ECDH").getProvider().getName()).isEqualTo("SunEC");
        assertThat(HttpEce.class.getProtectionDomain().getCodeSource().getLocation().toString())
                .contains("classes");

        KeyPair serverKeys = generateEcdhKeyPair();
        KeyPair userKeys = generateEcdhKeyPair();
        String userPublicKey = encode((ECPublicKey) userKeys.getPublic());
        String userAuth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);

        Notification notification = Notification.builder()
                .endpoint("https://push.example.test/subscription")
                .userPublicKey(userPublicKey)
                .userAuth(userAuth)
                .payload("{}")
                .ttl(60)
                .build();

        var client = new WebPushConfig().webPushClient(
                encode((ECPublicKey) serverKeys.getPublic()),
                encode((ECPrivateKey) serverKeys.getPrivate()),
                "https://cfcbooking.io.vn");
        var request = client.preparePost(notification, Encoding.AES128GCM);

        assertThat(request.getEntity()).isNotNull();
        assertThat(request.getEntity().getContentLength()).isPositive();
        assertThat(request.getEntity().getContent().readAllBytes())
                .isNotEqualTo("{}".getBytes(StandardCharsets.UTF_8));
    }

    private KeyPair generateEcdhKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                "ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private String encode(ECPublicKey key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode(key));
    }

    private String encode(ECPrivateKey key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode(key));
    }
}
