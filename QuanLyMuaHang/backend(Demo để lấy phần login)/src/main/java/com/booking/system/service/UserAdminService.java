package com.booking.system.service;

import com.booking.system.dto.AdminResetPasswordRequest;
import com.booking.system.dto.AdminUpdateUserRequest;
import com.booking.system.dto.AdminUserResponse;
import com.booking.system.entity.Department;
import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import com.booking.system.repository.DepartmentRepository;
import com.booking.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> list(
            User principal,
            String query,
            RoleEnum role,
            UserStatus status,
            Pageable pageable) {
        requireAdmin(principal);
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return userRepository.searchForAdmin(normalizedQuery, role, status, pageable)
                .map(AdminUserResponse::from);
    }

    @Transactional
    public AdminUserResponse update(User principal, String userId, AdminUpdateUserRequest request) {
        User admin = requireAdmin(principal);
        User account = findUser(userId);

        if (admin.getId().equals(account.getId())
                && (request.role() != RoleEnum.ADMIN || request.status() != UserStatus.ACTIVE)) {
            throw new RuntimeException("Không thể tự hạ quyền hoặc khóa tài khoản Admin đang đăng nhập");
        }

        RoleEnum previousRole = account.getRole();
        UserStatus previousStatus = account.getStatus();
        account.setFullName(request.fullName().trim());
        account.setRole(request.role());
        account.setStatus(request.status());
        account.setDepartment(resolveDepartment(request.departmentId()));
        account.setJobPosition(normalizeOptional(request.jobPosition()));

        User saved = userRepository.save(account);
        if (previousRole != saved.getRole() || previousStatus != saved.getStatus()) {
            authService.revokeAllSessions(saved.getEmail());
        }
        return AdminUserResponse.from(saved);
    }

    @Transactional
    public void resetPassword(User principal, String userId, AdminResetPasswordRequest request) {
        requireAdmin(principal);
        User account = findUser(userId);
        account.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(account);
        authService.revokeAllSessions(account.getEmail());
    }

    private User requireAdmin(User principal) {
        if (principal == null || principal.getId() == null || principal.getRole() != RoleEnum.ADMIN) {
            throw new AccessDeniedException("Chỉ quản trị viên được quản lý tài khoản");
        }
        return userRepository.findById(principal.getId())
                .filter(user -> user.getRole() == RoleEnum.ADMIN && user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản quản trị viên không còn hợp lệ"));
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));
    }

    private Department resolveDepartment(String departmentId) {
        if (departmentId == null || departmentId.isBlank()) {
            return null;
        }
        return departmentRepository.findById(departmentId.trim())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng ban"));
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
