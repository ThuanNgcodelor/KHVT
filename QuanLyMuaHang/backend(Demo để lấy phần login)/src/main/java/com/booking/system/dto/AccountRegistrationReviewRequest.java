package com.booking.system.dto;

import jakarta.validation.constraints.Size;

public record AccountRegistrationReviewRequest(
        @Size(max = 1000, message = "Lý do không được vượt quá 1000 ký tự")
        String reason
) {
}
