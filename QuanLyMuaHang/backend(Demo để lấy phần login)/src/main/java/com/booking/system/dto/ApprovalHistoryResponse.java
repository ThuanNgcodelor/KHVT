package com.booking.system.dto;

import com.booking.system.entity.ApprovalStep;
import com.booking.system.entity.BookingCar;
import com.booking.system.entity.BookingRoom;
import com.booking.system.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApprovalHistoryResponse {
    private String id;
    private String bookingId;
    private String type;
    private String resourceName;
    private String purpose;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String reason;
    private LocalDateTime actedAt;
    private UserSummary requester;
    private UserSummary approver;

    @Data
    @Builder
    public static class UserSummary {
        private String id;
        private String fullName;
        private String email;
        private String avatarUrl;
        private String departmentName;

        public static UserSummary from(User user) {
            if (user == null) return null;
            return UserSummary.builder()
                    .id(user.getId())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .avatarUrl(user.getAvatarUrl())
                    .departmentName(user.getDepartment() == null ? null : user.getDepartment().getName())
                    .build();
        }
    }

    public static ApprovalHistoryResponse from(ApprovalStep step) {
        BookingRoom roomBooking = step.getBookingRoom();
        BookingCar carBooking = step.getBookingCar();
        boolean isRoom = roomBooking != null;
        boolean isCancelled = (isRoom ? roomBooking.getStatus() : carBooking.getStatus())
                == com.booking.system.enums.BookingStatus.CANCELLED;

        return ApprovalHistoryResponse.builder()
                .id(step.getId())
                .bookingId(isRoom ? roomBooking.getId() : carBooking.getId())
                .type(isRoom ? "ROOM" : "CAR")
                .resourceName(isRoom
                        ? roomBooking.getRoom().getName()
                        : carBooking.getVehicle().getVehicleType().getName() + " - " + carBooking.getVehicle().getLicensePlate())
                .purpose(isRoom
                        ? roomBooking.getTitle()
                        : carBooking.getDeparture() + " - " + carBooking.getDestination())
                .startTime(isRoom ? roomBooking.getStartTime() : carBooking.getStartTime())
                .endTime(isRoom ? roomBooking.getEndTime() : carBooking.getEndTime())
                .status((isRoom ? roomBooking.getStatus() : carBooking.getStatus()).name())
                .reason(isCancelled
                        ? (isRoom ? roomBooking.getCancelReason() : carBooking.getCancelReason())
                        : step.getReason())
                .actedAt(isCancelled
                        ? (isRoom ? roomBooking.getCancelledAt() : carBooking.getCancelledAt())
                        : step.getActedAt())
                .requester(UserSummary.from(isRoom ? roomBooking.getRequester() : carBooking.getRequester()))
                .approver(UserSummary.from(isCancelled
                        ? (isRoom ? roomBooking.getCancelledBy() : carBooking.getCancelledBy())
                        : step.getApprover()))
                .build();
    }
}
