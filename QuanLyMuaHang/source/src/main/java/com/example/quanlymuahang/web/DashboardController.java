package com.example.quanlymuahang.web;

import com.example.quanlymuahang.dashboard.application.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAuthority('*') or (hasAuthority('PO_READ') and hasAuthority('CATALOG_READ'))")
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard) { this.dashboard = dashboard; }
    @GetMapping public DashboardService.DashboardView dashboard() { return dashboard.get(); }
}
