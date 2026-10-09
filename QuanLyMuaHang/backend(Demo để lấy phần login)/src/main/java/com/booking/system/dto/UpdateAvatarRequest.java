package com.booking.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAvatarRequest(
        @NotBlank(message = "Ảnh đại diện không được để trống")
        @Size(max = 2_000_000, message = "Dữ liệu ảnh đại diện quá lớn")
        String avatarUrl
) {
}
