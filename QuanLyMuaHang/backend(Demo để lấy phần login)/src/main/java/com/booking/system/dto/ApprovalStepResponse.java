package com.booking.system.dto;

import com.booking.system.entity.ApprovalStep;
import com.booking.system.entity.User;
import com.booking.system.enums.ApprovalStatus;
import com.booking.system.enums.RoleEnum;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ApprovalStepResponse {
    private String id;
    private ApprovalStatus status;
    private String reason;
    private LocalDateTime actedAt;
    private Approver approver;

    public static ApprovalStepResponse from(ApprovalStep step) {
        return ApprovalStepResponse.builder()
                .id(step.getId())
                .status(step.getStatus())
                .reason(step.getReason())
                .actedAt(step.getActedAt())
                .approver(Approver.from(step.getApprover()))
                .build();
    }

    @Getter
    @Builder
    public static class Approver {
        private String id;
        private String fullName;
        private String avatarUrl;
        private RoleEnum role;
        private String jobPosition;
        private String departmentName;

        public static Approver from(User user) {
            if (user == null) {
                return null;
            }
            return Approver.builder()
                    .id(user.getId())
                    .fullName(user.getFullName())
                    .avatarUrl(user.getAvatarUrl())
                    .role(user.getRole())
                    .jobPosition(user.getJobPosition())
                    .departmentName(user.getDepartment() == null ? null : user.getDepartment().getName())
                    .build();
        }
    }
}
