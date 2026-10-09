package com.example.quanlymuahang.dashboard.application;

import com.example.quanlymuahang.procurement.application.PurchaseOrderService;
import com.example.quanlymuahang.repository.MaterialRepository;
import com.example.quanlymuahang.repository.PurchaseOrderRepository;
import com.example.quanlymuahang.repository.SupplierRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class DashboardService {
    private final PurchaseOrderRepository orders;
    private final MaterialRepository materials;
    private final SupplierRepository suppliers;
    private final ZoneId zone;

    public DashboardService(PurchaseOrderRepository orders, MaterialRepository materials, SupplierRepository suppliers,
                            @org.springframework.beans.factory.annotation.Value("${app.timezone:Asia/Ho_Chi_Minh}") String timezone) {
        this.orders = orders; this.materials = materials; this.suppliers = suppliers; this.zone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public DashboardView get() {
        LocalDate today = LocalDate.now(zone);
        LocalDate firstDay = today.withDayOfMonth(1);
        List<PurchaseOrderService.OrderView> recent = orders.search(null, null, PageRequest.of(0, 8)).getContent()
                .stream().map(PurchaseOrderService.OrderView::from).toList();
        return new DashboardView(orders.countByOrderDateBetween(firstDay, today), materials.countByActiveTrue(), suppliers.countByActiveTrue(), recent, Instant.now());
    }

    public record DashboardView(long purchaseOrdersThisMonth, long activeMaterials, long activeSuppliers,
                                List<PurchaseOrderService.OrderView> recentOrders, Instant generatedAt) {}
}
