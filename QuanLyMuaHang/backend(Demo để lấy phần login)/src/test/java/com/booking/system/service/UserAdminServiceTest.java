package com.booking.system.service;

import com.booking.system.dto.AdminResetPasswordRequest;
import com.booking.system.dto.AdminUpdateUserRequest;
import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import com.booking.system.repository.DepartmentRepository;
import com.booking.system.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthService authService;

    @InjectMocks private UserAdminService service;

    @Test
    void adminCanResetPasswordAndOldSessionsAreRevoked() {
        User admin = user("admin-id", "admin@example.test", RoleEnum.ADMIN, UserStatus.ACTIVE);
        User target = user("manager-id", "manager@example.test", RoleEnum.MANAGER, UserStatus.ACTIVE);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");

        service.resetPassword(admin, target.getId(), new AdminResetPasswordRequest("new-password"));

        assertThat(target.getPassword()).isEqualTo("encoded-password");
        verify(userRepository).save(target);
        verify(authService).revokeAllSessions(target.getEmail());
    }

    @Test
    void managerCannotManageOtherAccounts() {
        User manager = user("manager-id", "manager@example.test", RoleEnum.MANAGER, UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.resetPassword(
                manager,
                "someone-id",
                new AdminResetPasswordRequest("new-password")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCannotLockOrDemoteOwnAccount() {
        User admin = user("admin-id", "admin@example.test", RoleEnum.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        AdminUpdateUserRequest request = new AdminUpdateUserRequest(
                "Admin",
                RoleEnum.MANAGER,
                UserStatus.ACTIVE,
                null,
                null);

        assertThatThrownBy(() -> service.update(admin, admin.getId(), request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không thể tự hạ quyền");
    }

    private static User user(String id, String email, RoleEnum role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(role.name());
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
