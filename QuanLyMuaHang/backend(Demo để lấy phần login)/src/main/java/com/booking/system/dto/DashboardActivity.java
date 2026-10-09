package com.booking.system.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class DashboardActivity {
    private String id;
    private String type; // "ROOM" or "CAR"
    private String title;
    private String subtitle; // e.g. Destination for car, room name for room
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String requesterName;
}
