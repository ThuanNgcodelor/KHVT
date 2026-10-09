package com.booking.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProfileUpdateRequestCreateRequest {
    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    private String avatarUrl;

    @NotBlank(message = "Phòng ban không được để trống")
    private String departmentId;

    @NotBlank(message = "Chức vụ không được để trống")
    private String position;
}
