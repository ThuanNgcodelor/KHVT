package com.booking.system.dto;

import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 255, message = "Họ tên tối đa 255 ký tự")
        String fullName,

        @NotNull(message = "Vai trò không được để trống")
        RoleEnum role,

        @NotNull(message = "Trạng thái không được để trống")
        UserStatus status,

        String departmentId,

        @Size(max = 255, message = "Chức vụ tối đa 255 ký tự")
        String jobPosition
) {
}
