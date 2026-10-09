package com.booking.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookingRoomRequest {

    @NotBlank(message = "Mã phòng không được để trống")
    private String roomId;

    private String requesterId;

    @NotBlank(message = "Tiêu đề cuộc họp không được để trống")
    private String title;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    private LocalDateTime startTime;

    @NotNull(message = "Thời gian kết thúc không được để trống")
    private LocalDateTime endTime;

    private Integer attendeeCount;

    private String note;
}
