package com.booking.system.service;

import com.booking.system.entity.User;
import com.booking.system.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    void changesPasswordWhenCurrentPasswordMatches() {
        User user = user();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", user.getPassword())).thenReturn(true);
        when(passwordEncoder.matches("new-password", user.getPassword())).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        userProfileService.changePassword(user.getId(), "old-password", "new-password");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }

    @Test
    void rejectsPasswordChangeWhenCurrentPasswordIsWrong() {
        User user = user();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> userProfileService.changePassword(user.getId(), "wrong-password", "new-password"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Mật khẩu hiện tại không chính xác");

        verify(userRepository, never()).save(user);
    }

    @Test
    void updatesDataImageAvatarDirectly() {
        User user = user();
        String avatar = "data:image/jpeg;base64,aGVsbG8=";
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        User updated = userProfileService.updateAvatar(user.getId(), avatar);

        assertThat(updated.getAvatarUrl()).isEqualTo(avatar);
        verify(userRepository).save(user);
    }

    @Test
    void rejectsNonImageAvatarPayload() {
        User user = user();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userProfileService.updateAvatar(user.getId(), "https://example.com/not-allowed.jpg"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("không đúng định dạng");

        verify(userRepository, never()).save(user);
    }

    private User user() {
        User user = new User();
        user.setId("user-1");
        user.setPassword("old-hash");
        return user;
    }
}
