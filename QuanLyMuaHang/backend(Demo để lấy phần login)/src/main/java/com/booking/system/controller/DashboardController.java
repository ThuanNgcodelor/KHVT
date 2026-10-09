package com.booking.system.controller;

import com.booking.system.dto.AdminDashboardStats;
import com.booking.system.dto.ApiResponse;
import com.booking.system.dto.ClientDashboardStats;
import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<AdminDashboardStats>> getAdminStats() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getAdminStats(), "Lấy thống kê admin thành công"));
    }

    @GetMapping("/client")
    public ResponseEntity<ApiResponse<ClientDashboardStats>> getClientStats(
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getClientStats(currentUser.getId()),
                "Lấy thống kê client thành công"
        ));
    }

    /**
     * Giữ tương thích với client cũ nhưng không cho phép đọc dashboard của người khác.
     */
    @Deprecated
    @GetMapping("/client/{userId}")
    public ResponseEntity<ApiResponse<ClientDashboardStats>> getClientStatsLegacy(
            @PathVariable String userId,
            @AuthenticationPrincipal User currentUser
    ) {
        boolean isOwner = currentUser.getId().equals(userId);
        boolean isAdmin = currentUser.getRole() == RoleEnum.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Không có quyền xem dashboard của người dùng khác");
        }
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getClientStats(userId),
                "Lấy thống kê client thành công"
        ));
    }
}
