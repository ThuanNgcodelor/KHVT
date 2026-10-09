package com.booking.system.dto;

import com.booking.system.entity.User;
import com.booking.system.enums.UserStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AccountRegistrationResponse {
    private String id;
    private String fullName;
    private String email;
    private UserStatus status;
    private LocalDateTime registeredAt;
    private LocalDateTime reviewedAt;
    private String reviewedByName;
    private String reviewReason;

    public static AccountRegistrationResponse from(User user) {
        return AccountRegistrationResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .status(user.getStatus())
                .registeredAt(user.getCreatedAt())
                .reviewedAt(user.getRegistrationReviewedAt())
                .reviewedByName(user.getRegistrationReviewedBy() == null
                        ? null : user.getRegistrationReviewedBy().getFullName())
                .reviewReason(user.getRegistrationReviewReason())
                .build();
    }
}
