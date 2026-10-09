package com.booking.system.dto;

import com.booking.system.entity.ProfileUpdateRequest;
import com.booking.system.enums.ProfileUpdateRequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProfileUpdateRequestResponse {
    private String id;
    private ProfileUpdateRequestStatus status;
    private String currentFullName;
    private String currentAvatarUrl;
    private String currentDepartmentName;
    private String currentPosition;
    private String requestedFullName;
    private String requestedAvatarUrl;
    private DepartmentResponse requestedDepartment;
    private String requestedDepartmentName;
    private String requestedPosition;
    private String reviewReason;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private Snapshot requester;
    private Snapshot reviewedBy;

    public static ProfileUpdateRequestResponse from(ProfileUpdateRequest request) {
        return ProfileUpdateRequestResponse.builder()
                .id(request.getId())
                .status(request.getStatus())
                .currentFullName(request.getCurrentFullName())
                .currentAvatarUrl(request.getCurrentAvatarUrl())
                .currentDepartmentName(request.getCurrentDepartmentName())
                .currentPosition(request.getCurrentPosition())
                .requestedFullName(request.getRequestedFullName())
                .requestedAvatarUrl(request.getRequestedAvatarUrl())
                .requestedDepartment(request.getRequestedDepartment() == null ? null : DepartmentResponse.from(request.getRequestedDepartment()))
                .requestedDepartmentName(request.getRequestedDepartment() == null ? null : request.getRequestedDepartment().getName())
                .requestedPosition(request.getRequestedPosition())
                .reviewReason(request.getReviewReason())
                .requestedAt(request.getRequestedAt())
                .reviewedAt(request.getReviewedAt())
                .requester(Snapshot.from(request.getRequester()))
                .reviewedBy(request.getReviewedBy() == null ? null : Snapshot.from(request.getReviewedBy()))
                .build();
    }

    @Getter
    @Builder
    public static class Snapshot {
        private String id;
        private String fullName;
        private String avatarUrl;
        private String email;
        private String role;
        private String departmentName;
        private String position;

        public static Snapshot from(com.booking.system.entity.User user) {
            return Snapshot.builder()
                    .id(user.getId())
                    .fullName(user.getFullName())
                    .avatarUrl(user.getAvatarUrl())
                    .email(user.getEmail())
                    .role(user.getRole() == null ? null : user.getRole().name())
                    .departmentName(user.getDepartment() == null ? null : user.getDepartment().getName())
                    .position(user.getJobPosition())
                    .build();
        }
    }
}
