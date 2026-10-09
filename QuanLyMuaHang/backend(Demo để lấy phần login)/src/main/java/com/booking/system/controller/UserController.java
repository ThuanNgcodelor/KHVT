package com.booking.system.controller;

import com.booking.system.dto.AdminCreateUserRequest;
import com.booking.system.dto.AdminResetPasswordRequest;
import com.booking.system.dto.AdminUpdateUserRequest;
import com.booking.system.dto.AdminUserResponse;
import com.booking.system.dto.ApiResponse;
import com.booking.system.dto.AuthResponse;
import com.booking.system.dto.ChangePasswordRequest;
import com.booking.system.dto.UpdateAvatarRequest;
import com.booking.system.entity.Department;
import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import com.booking.system.repository.DepartmentRepository;
import com.booking.system.repository.UserRepository;
import com.booking.system.service.UserProfileService;
import com.booking.system.service.AccountRegistrationService;
import com.booking.system.service.UserAdminService;
import com.booking.system.dto.AccountRegistrationResponse;
import com.booking.system.dto.AccountRegistrationReviewRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileService userProfileService;
    private final AccountRegistrationService accountRegistrationService;
    private final UserAdminService userAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getUsers(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) RoleEnum role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
            return ResponseEntity.ok(ApiResponse.success(
                    userAdminService.list(currentUser, query, role, status, pageable),
                    "Lấy danh sách tài khoản thành công"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        }
    }

    @GetMapping("/registration-approvals")
    public ResponseEntity<ApiResponse<Page<AccountRegistrationResponse>>> getPendingRegistrations(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
            return ResponseEntity.ok(ApiResponse.success(
                    accountRegistrationService.getPending(currentUser, pageable),
                    "Lấy danh sách tài khoản chờ duyệt thành công"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        }
    }

    @GetMapping("/registration-approvals/count")
    public ResponseEntity<ApiResponse<Long>> countPendingRegistrations(@AuthenticationPrincipal User currentUser) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    accountRegistrationService.countPending(currentUser),
                    "Lấy số tài khoản chờ duyệt thành công"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        }
    }

    @PatchMapping("/{id}/approve-registration")
    public ResponseEntity<ApiResponse<AccountRegistrationResponse>> approveRegistration(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    accountRegistrationService.approve(currentUser, id),
                    "Đã phê duyệt tài khoản"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PatchMapping("/{id}/reject-registration")
    public ResponseEntity<ApiResponse<AccountRegistrationResponse>> rejectRegistration(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id,
            @Valid @RequestBody AccountRegistrationReviewRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    accountRegistrationService.reject(currentUser, id, request.reason()),
                    "Đã từ chối tài khoản"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse.UserDto>> me(@AuthenticationPrincipal User user) {
        User current = requireUser(user);
        return ResponseEntity.ok(ApiResponse.success(toUserDto(current), "Lấy thông tin người dùng thành công"));
    }

    @PatchMapping("/me/avatar")
    public ResponseEntity<ApiResponse<AuthResponse.UserDto>> updateAvatar(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateAvatarRequest request
    ) {
        try {
            User updated = userProfileService.updateAvatar(requireUser(user).getId(), request.avatarUrl());
            return ResponseEntity.ok(ApiResponse.success(toUserDto(updated), "Cập nhật ảnh đại diện thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        try {
            userProfileService.changePassword(requireUser(user).getId(), request.currentPassword(), request.newPassword());
            return ResponseEntity.ok(ApiResponse.success(null, "Đổi mật khẩu thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/approvers")
    public ResponseEntity<ApiResponse<List<User>>> getApprovers() {
        // Lấy danh sách ADMIN và MANAGER
        List<User> approvers = userRepository.findByRoleIn(Arrays.asList(RoleEnum.ADMIN, RoleEnum.MANAGER));
        // Có thể sắp xếp Admin lên trước bằng Java Stream
        approvers.sort((u1, u2) -> {
            if (u1.getRole() == RoleEnum.ADMIN && u2.getRole() != RoleEnum.ADMIN) return -1;
            if (u1.getRole() != RoleEnum.ADMIN && u2.getRole() == RoleEnum.ADMIN) return 1;
            return 0;
        });
        return ResponseEntity.ok(ApiResponse.success(approvers, "Lấy danh sách người duyệt thành công"));
    }

    // Admin tạo tài khoản email/password cho nhân sự nội bộ.
    @PostMapping
    public ResponseEntity<ApiResponse<AuthResponse.UserDto>> createUser(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        try {
            requireAdmin(currentUser);

            String email = normalizeEmail(request.email());
            if (userRepository.existsByEmail(email)) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "Email đã tồn tại trong hệ thống"));
            }

            RoleEnum role = request.role() == null ? RoleEnum.MANAGER : request.role();
            if (role == RoleEnum.EMPLOYEE) {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        400,
                        "EMPLOYEE không được phép đăng nhập; hãy tạo tài khoản MANAGER hoặc ADMIN"));
            }

            Department department = null;
            if (request.departmentId() != null && !request.departmentId().isBlank()) {
                department = departmentRepository.findById(request.departmentId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng ban"));
            }

            User user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(request.password()));
            user.setFullName(resolveFullName(request.fullName(), email));
            user.setRole(role);
            user.setStatus(UserStatus.ACTIVE);
            user.setDepartment(department);
            user.setJobPosition(request.jobPosition());

            User savedUser = userRepository.save(user);
            return ResponseEntity.ok(ApiResponse.success(toUserDto(savedUser), "Tạo tài khoản thành công"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUser(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    userAdminService.update(currentUser, id, request),
                    "Cập nhật tài khoản thành công"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<ApiResponse<Void>> resetUserPassword(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String id,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        try {
            userAdminService.resetPassword(currentUser, id, request);
            return ResponseEntity.ok(ApiResponse.success(null, "Đặt lại mật khẩu thành công"));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    private User requireUser(User user) {
        if (user == null) {
            throw new RuntimeException("Chưa đăng nhập");
        }
        return user;
    }

    private void requireAdmin(User user) {
        User current = requireUser(user);
        if (current.getRole() != RoleEnum.ADMIN) {
            throw new AccessDeniedException("Chỉ quản trị viên được thực hiện thao tác này");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String resolveFullName(String fullName, String email) {
        if (fullName != null && !fullName.isBlank()) {
            return fullName.trim();
        }
        return email.substring(0, email.indexOf("@"));
    }

    private AuthResponse.UserDto toUserDto(User user) {
        return new AuthResponse.UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getAvatarUrl(),
                user.getDepartment() == null ? null : user.getDepartment().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getName(),
                user.getJobPosition(),
                user.getPassword() != null
        );
    }
}
