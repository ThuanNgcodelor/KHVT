package com.example.quanlymuahang.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Thin REST adapter for the first vertical slice.
 * Business use cases will move into the procurement application context as it grows.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @GetMapping
    public DashboardResponse dashboard() {
        return new DashboardResponse(
                new DashboardKpi("PO tháng này", 0, "Chưa có dữ liệu"),
                new DashboardKpi("Vật tư đang quản lý", 0, "Sẵn sàng import workbook"),
                new DashboardKpi("Nhà cung cấp", 0, "Sẵn sàng đồng bộ danh mục"),
                List.of(),
                Instant.now()
        );
    }

    public record DashboardResponse(
            DashboardKpi purchaseOrders,
            DashboardKpi materials,
            DashboardKpi suppliers,
            List<RecentOrder> recentOrders,
            Instant generatedAt
    ) {}

    public record DashboardKpi(String label, long value, String hint) {}

    public record RecentOrder(String poNumber, String supplier, String status, String total) {}
}
