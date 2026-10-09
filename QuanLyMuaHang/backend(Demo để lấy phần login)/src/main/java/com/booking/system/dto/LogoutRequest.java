package com.booking.system.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class LogoutRequest {
    @NotBlank(message = "Refresh token không được để trống")
    private String refreshToken;
}
