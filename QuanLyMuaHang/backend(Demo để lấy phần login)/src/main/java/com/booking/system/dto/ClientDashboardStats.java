package com.booking.system.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ClientDashboardStats {
    private List<DashboardActivity> upcomingBookings;
}
