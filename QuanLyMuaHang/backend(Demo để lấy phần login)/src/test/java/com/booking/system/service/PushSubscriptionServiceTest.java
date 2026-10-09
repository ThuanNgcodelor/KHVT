package com.booking.system.service;

import com.booking.system.dto.PushSubscriptionRequest;
import com.booking.system.dto.PushSubscriptionResponse;
import com.booking.system.entity.PushSubscription;
import com.booking.system.entity.User;
import com.booking.system.repository.PushSubscriptionRepository;
import com.booking.system.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PushSubscriptionService pushSubscriptionService;

    @Test
    void subscribesWithAtomicUpsertAndReturnsPersistedSubscription() {
        User user = new User();
        user.setId("user-1");

        PushSubscriptionRequest request = new PushSubscriptionRequest();
        request.setEndpoint("https://push.example.test/device-1");
        request.setP256dh("p256dh-key");
        request.setAuth("auth-key");
        request.setDeviceType("ios");
        request.setUserAgent("mobile-browser");

        PushSubscription persisted = new PushSubscription();
        persisted.setId("subscription-1");
        persisted.setUser(user);
        persisted.setEndpoint(request.getEndpoint());
        persisted.setP256dhKey(request.getP256dh());
        persisted.setAuthKey(request.getAuth());
        persisted.setDeviceType(request.getDeviceType());
        persisted.setUserAgent(request.getUserAgent());
        persisted.setActive(true);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(pushSubscriptionRepository.findByEndpoint(request.getEndpoint()))
                .thenReturn(Optional.of(persisted));

        PushSubscriptionResponse response = pushSubscriptionService.subscribe(user.getId(), request);

        verify(pushSubscriptionRepository).upsert(
                anyString(),
                eq(user.getId()),
                eq(request.getEndpoint()),
                eq(request.getP256dh()),
                eq(request.getAuth()),
                isNull(),
                eq(request.getDeviceType()),
                eq(request.getUserAgent()),
                any(LocalDateTime.class)
        );
        assertThat(response.getId()).isEqualTo(persisted.getId());
        assertThat(response.getEndpoint()).isEqualTo(request.getEndpoint());
        assertThat(response.isActive()).isTrue();
    }
}
