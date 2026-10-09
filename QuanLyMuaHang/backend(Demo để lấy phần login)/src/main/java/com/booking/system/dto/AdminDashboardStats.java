package com.booking.system.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AdminDashboardStats {
    private long activeRooms;
    private long totalRooms;
    private long activeCars;
    private long totalCars;
    private long pendingApprovals;
    private List<DashboardActivity> todayActivities;
}
