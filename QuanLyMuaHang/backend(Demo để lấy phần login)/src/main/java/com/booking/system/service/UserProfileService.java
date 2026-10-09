package com.booking.system.service;

import com.booking.system.entity.User;
import com.booking.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final String DATA_IMAGE_PREFIX_PATTERN = "^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=\\r\\n]+$";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User updateAvatar(String userId, String avatarUrl) {
        User user = findUser(userId);
        String normalizedAvatar = avatarUrl == null ? "" : avatarUrl.trim();
        if (!normalizedAvatar.matches(DATA_IMAGE_PREFIX_PATTERN)) {
            throw new RuntimeException("Ảnh đại diện không đúng định dạng JPEG, PNG hoặc WebP");
        }

        user.setAvatarUrl(normalizedAvatar);
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = findUser(userId);
        if (user.getPassword() == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Mật khẩu hiện tại không chính xác");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new RuntimeException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
    }
}
