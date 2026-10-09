package com.booking.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh Token không được để trống")
    private String refreshToken;
}
