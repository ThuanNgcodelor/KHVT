package com.booking.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterVerifyRequest(
        @Email(message = "Email không hợp lệ")
        @NotBlank(message = "Email không được để trống")
        String email,

        @NotBlank(message = "OTP không được để trống")
        @Size(min = 6, max = 6, message = "OTP phải gồm 6 số")
        String otp,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 255, message = "Họ tên không được vượt quá 255 ký tự")
        String fullName,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, max = 72, message = "Mật khẩu phải từ 6 đến 72 ký tự")
        String password
) {
}
