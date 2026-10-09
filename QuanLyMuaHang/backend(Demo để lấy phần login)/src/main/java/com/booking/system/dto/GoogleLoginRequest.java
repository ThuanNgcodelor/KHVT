package com.booking.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {
    @NotBlank(message = "Token từ Google không được để trống")
    private String idToken;
}
