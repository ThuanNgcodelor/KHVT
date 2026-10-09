package com.booking.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookingCarRequest {

    private String requesterId;

    @NotBlank(message = "ID xe không được để trống")
    private String vehicleId;
    
    @NotBlank(message = "Điểm đi không được để trống")
    private String departure;

    @NotBlank(message = "Điểm đến không được để trống")
    private String destination;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    private LocalDateTime startTime;

    @NotNull(message = "Thời gian kết thúc không được để trống")
    private LocalDateTime endTime;

    private String note;
}
