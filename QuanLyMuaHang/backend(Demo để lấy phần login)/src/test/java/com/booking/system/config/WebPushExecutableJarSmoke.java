package com.booking.system.config;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

public final class WebPushExecutableJarSmoke {

    private WebPushExecutableJarSmoke() {
    }

    public static void main(String[] args) throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        KeyPair serverKeys = generateEcdhKeyPair();
        KeyPair userKeys = generateEcdhKeyPair();
        String userAuth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);

        Notification notification = Notification.builder()
                .endpoint("https://push.example.test/executable-jar-smoke")
                .userPublicKey(encode((ECPublicKey) userKeys.getPublic()))
                .userAuth(userAuth)
                .payload("{}")
                .ttl(60)
                .build();

        var client = new WebPushConfig().webPushClient(
                encode((ECPublicKey) serverKeys.getPublic()),
                encode((ECPrivateKey) serverKeys.getPrivate()),
                "https://cfcbooking.io.vn"
        );
        var request = client.preparePost(notification, Encoding.AES128GCM);

        if (request.getEntity() == null || request.getEntity().getContentLength() <= 0) {
            throw new IllegalStateException("Executable JAR Web Push encryption produced no payload");
        }

        String providerLocation = BouncyCastleProvider.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString();
        System.out.println("WEB_PUSH_EXECUTABLE_JAR_SMOKE_OK provider=" + providerLocation);
    }

    private static KeyPair generateEcdhKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                "ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String encode(ECPublicKey key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode(key));
    }

    private static String encode(ECPrivateKey key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode(key));
    }
}
