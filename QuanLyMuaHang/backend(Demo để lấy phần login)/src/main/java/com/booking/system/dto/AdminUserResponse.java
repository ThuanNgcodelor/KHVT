package com.booking.system.dto;

import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;

import java.time.LocalDateTime;

public record AdminUserResponse(
        String id,
        String email,
        String fullName,
        RoleEnum role,
        UserStatus status,
        String departmentId,
        String departmentName,
        String jobPosition,
        boolean hasPassword,
        LocalDateTime createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                user.getDepartment() == null ? null : user.getDepartment().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getName(),
                user.getJobPosition(),
                user.getPassword() != null,
                user.getCreatedAt());
    }
}
