package com.booking.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @Email(message = "Email không hợp lệ")
        @NotBlank(message = "Email không được để trống")
        String email,

        @NotBlank(message = "OTP không được để trống")
        @Size(min = 6, max = 6, message = "OTP phải gồm 6 số")
        String otp,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        String newPassword
) {
}
